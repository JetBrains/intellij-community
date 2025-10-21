// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.JavaLightStubBuilder
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.impl.source.tree.JavaElementType.*
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.psi.stubs.StubInconsistencyReporter
import com.intellij.psi.stubs.StubTextInconsistencyException
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.gist.GistManager
import java.util.*
import kotlin.collections.HashMap


private val gist = GistManager.getInstance().newPsiFileGist("contractInference", 14, MethodDataExternalizer) { file ->
  indexFile(file.node.lighterAST)
}

private fun indexFile(tree: LighterAST): Map<Int, MethodData> {
  val visitor = InferenceVisitor(tree)
  visitor.visitNode(tree.root)
  return visitor.result
}

internal data class ClassData(val hasSuper: Boolean, val hasPureInitializer: Boolean, val isFinal: Boolean,
                              val onlyLocalInheritors: Boolean, val fieldModifiers: Map<String, LighterASTNode?>)

private class InferenceVisitor(val tree : LighterAST) : RecursiveLighterASTNodeWalkingVisitor(tree) {
  var methodIndex = 0
  val classData = HashMap<LighterASTNode, ClassData>()
  val result = HashMap<Int, MethodData>()

  override fun visitNode(element: LighterASTNode) {
    when(element.tokenType) {
      CLASS, ANONYMOUS_CLASS -> {
        classData[element] = calcClassData(element)
      }
      METHOD -> {
        calcData(element)?.let { data -> result[methodIndex] = data }
        methodIndex++
      }
    }

    if (JavaLightStubBuilder.isCodeBlockWithoutStubs(element)) return
    super.visitNode(element)
  }

  private fun calcClassData(aClass: LighterASTNode) : ClassData {
    var hasSuper = aClass.tokenType == ANONYMOUS_CLASS
    var isFinal = aClass.tokenType == ANONYMOUS_CLASS
    val parent = tree.getParent(aClass)
    var onlyLocalInheritors = parent != null && parent.tokenType == DECLARATION_STATEMENT
    val fieldModifiers = HashMap<String, LighterASTNode?>()
    val initializers = ArrayList<LighterASTNode>()
    for (child in tree.getChildren(aClass)) {
      when(child.tokenType) {
        JavaTokenType.RECORD_KEYWORD, JavaTokenType.ENUM_KEYWORD -> isFinal = true
        MODIFIER_LIST -> {
          isFinal = LightTreeUtil.firstChildOfType(tree, child, JavaTokenType.FINAL_KEYWORD) != null
          if (!onlyLocalInheritors) {
            onlyLocalInheritors = (LightTreeUtil.firstChildOfType(tree, child, JavaTokenType.PRIVATE_KEYWORD) != null)
          }
        }
        ENUM_CONSTANT -> {
          // We rely on enum constants going after ENUM_KEYWORD
          isFinal = isFinal && LightTreeUtil.firstChildOfType(tree, child, ENUM_CONSTANT_INITIALIZER) == null
        }
        EXTENDS_LIST -> {
          if (LightTreeUtil.firstChildOfType(tree, child, JAVA_CODE_REFERENCE) != null) {
            hasSuper = true
          }
        }
        FIELD -> {
          val fieldName = JavaLightTreeUtil.getNameIdentifierText(tree, child)
          if (fieldName != null) {
            val modifiers = LightTreeUtil.firstChildOfType(tree, child, MODIFIER_LIST)
            fieldModifiers[fieldName] = modifiers
            val isStatic = LightTreeUtil.firstChildOfType(tree, modifiers, JavaTokenType.STATIC_KEYWORD) != null
            if (!isStatic) {
              val initializer = JavaLightTreeUtil.findExpressionChild(tree, child)
              if (initializer != null) {
                initializers.add(initializer)
              }
            }
          }
        }
        CLASS_INITIALIZER -> {
          val modifiers = LightTreeUtil.firstChildOfType(tree, child, MODIFIER_LIST)
          val isStatic = LightTreeUtil.firstChildOfType(tree, modifiers, JavaTokenType.STATIC_KEYWORD) != null
          if (!isStatic) {
            val body = LightTreeUtil.firstChildOfType(tree, child, CODE_BLOCK)
            if (body != null) {
              initializers.add(body)
            }
          }
        }
      }
    }
    var pureInitializer = true
    if (initializers.isNotEmpty()) {
      val visitor = PurityInferenceVisitor(tree, aClass, fieldModifiers, true)
      for (initializer in initializers) {
        walkMethodBody(initializer, visitor::visitNode)
        val result = visitor.result
        pureInitializer = result != null && result.singleCall == null && result.mutatedRefs.isEmpty()
        if (!pureInitializer) break
      }
    }
    return ClassData(hasSuper, pureInitializer, isFinal, onlyLocalInheritors, fieldModifiers)
  }
  
  private fun getInferenceMode(method: LighterASTNode, clsData: ClassData?): JavaSourceInference.InferenceMode {
    if (clsData?.isFinal == true || clsData?.onlyLocalInheritors == true) return JavaSourceInference.InferenceMode.ENABLED
    // PsiUtil#canBeOverridden logic on LighterAST
    val ctor = LightTreeUtil.firstChildOfType(tree, method, TYPE) == null
    if (ctor) return JavaSourceInference.InferenceMode.ENABLED
    val modifiers = LightTreeUtil.firstChildOfType(tree, method, MODIFIER_LIST)
    val isStatic = LightTreeUtil.firstChildOfType(tree, modifiers, JavaTokenType.STATIC_KEYWORD) != null
    val isFinal = LightTreeUtil.firstChildOfType(tree, modifiers, JavaTokenType.FINAL_KEYWORD) != null
    val isPrivate = LightTreeUtil.firstChildOfType(tree, modifiers, JavaTokenType.PRIVATE_KEYWORD) != null
    if (isStatic || isFinal || isPrivate) return JavaSourceInference.InferenceMode.ENABLED
    return JavaSourceInference.InferenceMode.PARAMETERS
  }

  private fun calcData(method: LighterASTNode): MethodData? {
    val body = LightTreeUtil.firstChildOfType(tree, method, CODE_BLOCK) ?: return null
    val parameterNames = getParameterNames(tree, method)
    val clsData = classData[tree.getParent(method)]
    val inferenceMode = getInferenceMode(method, clsData)
    if (inferenceMode == JavaSourceInference.InferenceMode.PARAMETERS && parameterNames.isEmpty()) {
      return null
    }
    val statements = ContractInferenceInterpreter.getStatements(body, tree)
    val notNullParams = inferNotNullParameters(tree, parameterNames, statements)
    if (inferenceMode == JavaSourceInference.InferenceMode.PARAMETERS) {
      return createData(body, emptyList(), null, null, notNullParams)
    }
    val fieldMap = clsData?.fieldModifiers ?: emptyMap()
    // Constructor which has super classes may implicitly call impure super constructor, so don't infer purity for subclasses
    val ctor = LightTreeUtil.firstChildOfType(tree, method, TYPE) == null
    val maybeImpureCtor = ctor && (clsData == null || clsData.hasSuper || !clsData.hasPureInitializer)
    
    val contractInference = ContractInferenceInterpreter(tree, method, body)
    val contracts = contractInference.inferContracts(statements)

    val nullityVisitor = MethodReturnInferenceVisitor(tree, contractInference.parameters, body)
    val purityVisitor = PurityInferenceVisitor(tree, body, fieldMap, ctor)
    var stopPurityAnalysis = maybeImpureCtor
    for (statement in statements) {
      walkMethodBody(statement) {
        nullityVisitor.visitNode(it)
        if (!stopPurityAnalysis) {
          stopPurityAnalysis = !purityVisitor.visitNode(it)
        }
        true
      }
    }

    return createData(body, contracts, nullityVisitor.result, if (maybeImpureCtor) null else purityVisitor.result, notNullParams)
  }

  private fun walkMethodBody(root: LighterASTNode, processor: (LighterASTNode) -> Boolean) {
    object : RecursiveLighterASTNodeWalkingVisitor(tree) {
      override fun visitNode(element: LighterASTNode) {
        val type = element.tokenType
        if (type === CLASS || type === FIELD || type === METHOD || type === ANNOTATION_METHOD || type === LAMBDA_EXPRESSION) return

        if (!processor(element)) {
          stopWalking()
        }
        super.visitNode(element)
      }
    }.visitNode(root)
  }

  private fun createData(body: LighterASTNode,
                         contracts: List<PreContract>,
                         methodReturn: MethodReturnInferenceResult?,
                         purity: PurityInferenceResult?,
                         notNullParams: BitSet): MethodData? {
    if (methodReturn == null && purity == null && contracts.isEmpty() && notNullParams.isEmpty) return null

    return MethodData(methodReturn, purity, contracts, notNullParams, body.startOffset, body.endOffset)
  }
}

public fun handleInconsistency(method: PsiMethodImpl, cachedData: MethodData, e: RuntimeException): RuntimeException {
  if (e is ProcessCanceledException) return e

  val file = method.containingFile
  val gistMap = gist.getFileData(file)
  GistManager.getInstance().invalidateData(file.viewProvider.virtualFile)

  val psiMap = indexFile(file.node.lighterAST)
  if (gistMap != psiMap) {
    GistManager.getInstance().invalidateData(file.viewProvider.virtualFile)

    return RuntimeExceptionWithAttachments("Gist outdated", e,
                                           Attachment("persisted.txt", gistMap.toString()),
                                           Attachment("psi.txt", psiMap.toString()))
  }

  StubTextInconsistencyException.checkStubTextConsistency(file, StubInconsistencyReporter.SourceOfCheck.CheckAfterExceptionInJava)
  val actualData = bindMethods(psiMap, file)[method]
  if (actualData != cachedData) {
    return RuntimeExceptionWithAttachments("Cache outdated",
                                           Attachment("actual.txt", actualData.toString()),
                                           Attachment("cached.txt", cachedData.toString()))

  }

  return e
}

public fun getIndexedData(method: PsiMethodImpl): MethodData? {
  val file = method.containingFile
  val map = CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result.create(bindMethods(gist.getFileData(file), file), file)
  }
  return map[method]
}

private fun bindMethods(fileData: Map<Int, MethodData>?, file: PsiFile): Map<PsiMethod, MethodData> {
  val result = hashMapOf<PsiMethod, MethodData>()
  if (fileData != null) {
    val spine = (file as PsiFileImpl).stubbedSpine
    var methodIndex = 0
    for (i in 0 until spine.stubCount) {
      if (spine.getStubType(i) === METHOD) {
        fileData[methodIndex]?.let { result[spine.getStubPsi(i) as PsiMethod] = it }
        methodIndex++
      }
    }
  }
  return result
}