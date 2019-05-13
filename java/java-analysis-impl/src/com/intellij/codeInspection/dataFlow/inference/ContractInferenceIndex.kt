// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.JavaLightStubBuilder
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.impl.source.tree.JavaElementType.*
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.gist.GistManager
import java.util.*
import kotlin.collections.HashMap

/**
 * @author peter
 */

private val gist = GistManager.getInstance().newPsiFileGist("contractInference", 10, MethodDataExternalizer) { file ->
  indexFile(file.node.lighterAST)
}

private fun indexFile(tree: LighterAST): Map<Int, MethodData> {
  val visitor = InferenceVisitor(tree)
  visitor.visitNode(tree.root)
  return visitor.result
}

internal data class ClassData(val hasSuper : Boolean, val hasPureInitializer : Boolean, val fieldModifiers : Map<String, LighterASTNode?>)

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
    val fieldModifiers = HashMap<String, LighterASTNode?>()
    val initializers = ArrayList<LighterASTNode>()
    for (child in tree.getChildren(aClass)) {
      when(child.tokenType) {
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
    if (!initializers.isEmpty()) {
      val visitor = PurityInferenceVisitor(tree, aClass, fieldModifiers, true)
      for (initializer in initializers) {
        walkMethodBody(initializer, visitor::visitNode)
        val result = visitor.result
        pureInitializer = result != null && result.singleCall == null && result.mutatedRefs.isEmpty()
        if (!pureInitializer) break
      }
    }
    return ClassData(hasSuper, pureInitializer, fieldModifiers)
  }

  private fun calcData(method: LighterASTNode): MethodData? {
    val body = LightTreeUtil.firstChildOfType(tree, method, CODE_BLOCK) ?: return null
    val clsData = classData[tree.getParent(method)]
    val fieldMap = clsData?.fieldModifiers ?: emptyMap()
    // Constructor which has super classes may implicitly call impure super constructor, so don't infer purity for subclasses
    val ctor = clsData != null && !clsData.hasSuper && clsData.hasPureInitializer && 
               LightTreeUtil.firstChildOfType(tree, method, TYPE) == null
    val statements = ContractInferenceInterpreter.getStatements(body, tree)

    val contracts = ContractInferenceInterpreter(tree, method, body).inferContracts(statements)

    val nullityVisitor = MethodReturnInferenceVisitor(tree, body)
    val purityVisitor = PurityInferenceVisitor(tree, body, fieldMap, ctor)
    for (statement in statements) {
      walkMethodBody(statement) { nullityVisitor.visitNode(it); purityVisitor.visitNode(it) }
    }
    val notNullParams = inferNotNullParameters(tree, method, statements)

    return createData(body, contracts, nullityVisitor.result, purityVisitor.result, notNullParams)
  }

  private fun walkMethodBody(root: LighterASTNode, processor: (LighterASTNode) -> Unit) {
    object : RecursiveLighterASTNodeWalkingVisitor(tree) {
      override fun visitNode(element: LighterASTNode) {
        val type = element.tokenType
        if (type === CLASS || type === FIELD || type === METHOD || type === ANNOTATION_METHOD || type === LAMBDA_EXPRESSION) return

        processor(element)
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

fun getIndexedData(method: PsiMethodImpl): MethodData? {
  val file = method.containingFile
  val map = CachedValuesManager.getCachedValue(file) {
    val fileData = gist.getFileData(file)
    val result = hashMapOf<PsiMethod, MethodData>()
    if (fileData != null) {
      val spine = (file as PsiFileImpl).stubbedSpine
      var methodIndex = 0
      for (i in 0 until spine.stubCount) {
        if (spine.getStubType(i) === JavaElementType.METHOD) {
          fileData[methodIndex]?.let { result[spine.getStubPsi(i) as PsiMethod] = it }
          methodIndex++
        }
      }
    }
    CachedValueProvider.Result.create(result, file)
  }
  return map[method]
}