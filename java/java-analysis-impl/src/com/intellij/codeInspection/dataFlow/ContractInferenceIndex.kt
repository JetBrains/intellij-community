/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.psi.impl.source.JavaLightStubBuilder
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

private val gist = GistManager.getInstance().newPsiFileGist("contractInference", 3, MethodDataExternalizer) { file ->
  indexFile(file.node.lighterAST)
}

private fun indexFile(tree: LighterAST): Map<Int, MethodData> {
  val result = HashMap<Int, MethodData>()

  object : RecursiveLighterASTNodeWalkingVisitor(tree) {
    var methodIndex = 0

    override fun visitNode(element: LighterASTNode) {
      if (element.tokenType === METHOD) {
        calcData(tree, element)?.let { data -> result[methodIndex] = data }
        methodIndex++
      }

      if (JavaLightStubBuilder.isCodeBlockWithoutStubs(element)) return

      super.visitNode(element)
    }
  }.visitNode(tree.root)

  return result
}

private fun calcData(tree: LighterAST, method: LighterASTNode): MethodData? {
  val body = LightTreeUtil.firstChildOfType(tree, method, CODE_BLOCK) ?: return null
  val statements = ContractInferenceInterpreter.getStatements(body, tree)

  val contracts = ContractInferenceInterpreter(tree, method, body).inferContracts(statements)

  val nullityVisitor = NullityInference.NullityInferenceVisitor(tree, body)
  val purityVisitor = PurityInference.PurityInferenceVisitor(tree, body)
  for (statement in statements) {
    walkMethodBody(tree, statement) { nullityVisitor.visitNode(it); purityVisitor.visitNode(it) }
  }
  val notNullParams = inferNotNullParameters(tree, method, statements)

  return createData(body, contracts, nullityVisitor.result, purityVisitor.result, notNullParams)
}

private fun walkMethodBody(tree: LighterAST, root: LighterASTNode, processor: (LighterASTNode) -> Unit) {
  object : RecursiveLighterASTNodeWalkingVisitor(tree) {
    override fun visitNode(element: LighterASTNode) {
      val type = element.tokenType
      if (type === CLASS || type === FIELD || type == METHOD || type == ANNOTATION_METHOD || type === LAMBDA_EXPRESSION) return

      processor(element)
      super.visitNode(element)
    }
  }.visitNode(root)
}

private fun createData(body: LighterASTNode,
                       contracts: List<PreContract>,
                       nullity: NullityInferenceResult?,
                       purity: PurityInferenceResult?,
                       notNullParams: BitSet): MethodData? {
  if (nullity == null && purity == null && contracts.isEmpty() && notNullParams.isEmpty) return null

  return MethodData(nullity, purity, contracts, notNullParams, body.startOffset, body.endOffset)
}

fun getIndexedData(method: PsiMethodImpl): MethodData? = gist.getFileData(method.containingFile)?.get(JavaStubImplUtil.getMethodStubIndex(method))