// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.psi.JavaTokenType
import com.intellij.psi.impl.source.JavaLightStubBuilder
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.impl.source.tree.JavaElementType.*
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.psi.stub.JavaStubImplUtil
import com.intellij.util.gist.GistManager
import java.util.*

/**
 * @author peter
 */

private val gist = GistManager.getInstance().newPsiFileGist("contractInference", 9, MethodDataExternalizer) { file ->
  indexFile(file.node.lighterAST)
}

private fun indexFile(tree: LighterAST): Map<Int, MethodData> {
  val visitor = InferenceVisitor(tree)
  visitor.visitNode(tree.root)
  return visitor.result
}

private class InferenceVisitor(val tree : LighterAST) : RecursiveLighterASTNodeWalkingVisitor(tree) {
  var methodIndex = 0
  val volatileFieldNames = HashSet<String>()
  val result = HashMap<Int, MethodData>()

  override fun visitNode(element: LighterASTNode) {
    when(element.tokenType) {
      CLASS -> gatherFields(element)
      METHOD -> {
        calcData(element)?.let { data -> result[methodIndex] = data }
        methodIndex++
      }
    }

    if (JavaLightStubBuilder.isCodeBlockWithoutStubs(element)) return
    super.visitNode(element)
  }

  private fun gatherFields(aClass: LighterASTNode) {
    val fields = LightTreeUtil.getChildrenOfType(tree, aClass, FIELD)
    for (field in fields) {
      val fieldName = JavaLightTreeUtil.getNameIdentifierText(tree, field)
      if (fieldName != null && JavaLightTreeUtil.hasExplicitModifier(tree, field, JavaTokenType.VOLATILE_KEYWORD)) {
        volatileFieldNames.add(fieldName)
      }
    }
  }

  private fun calcData(method: LighterASTNode): MethodData? {
    val body = LightTreeUtil.firstChildOfType(tree, method, CODE_BLOCK) ?: return null
    val statements = ContractInferenceInterpreter.getStatements(body, tree)

    val contracts = ContractInferenceInterpreter(tree, method, body).inferContracts(statements)

    val nullityVisitor = MethodReturnInferenceVisitor(tree, body)
    val purityVisitor = PurityInferenceVisitor(tree, body, volatileFieldNames)
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

fun getIndexedData(method: PsiMethodImpl): MethodData? = gist.getFileData(method.containingFile)?.get(JavaStubImplUtil.getMethodStubIndex(method))