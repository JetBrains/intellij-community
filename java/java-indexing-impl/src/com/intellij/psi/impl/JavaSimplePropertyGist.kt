// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.cache.RecordUtil
import com.intellij.psi.impl.source.JavaLightStubBuilder
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.tree.ElementType
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.psi.stub.JavaStubImplUtil
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.PsiFileGist
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.EnumeratorStringDescriptor
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.VisibleForTesting
import java.io.DataInput
import java.io.DataOutput

fun getFieldOfGetter(method: PsiMethod): PsiField? = resolveFieldFromIndexValue(method, true)

fun getFieldOfSetter(method: PsiMethod): PsiField? = resolveFieldFromIndexValue(method, false)

private fun resolveFieldFromIndexValue(method: PsiMethod, isGetter: Boolean): PsiField? {
  val file = method.containingFile
  if (file.fileType != JavaFileType.INSTANCE) return null
  val id = JavaStubImplUtil.getMethodStubIndex(method)
  if (id != -1) {
    return javaSimplePropertyGist.getFileData(file).get(id)?.let { indexValue ->
      if (isGetter != indexValue.getter) return null
      val psiClass = method.containingClass
      val project = psiClass!!.project
      val expr = JavaPsiFacade.getElementFactory(project).createExpressionFromText(indexValue.propertyRefText, psiClass)
      return PropertyUtilBase.getSimplyReturnedField(expr)
    }
  }
  return null
}

@VisibleForTesting
val javaSimplePropertyGist: PsiFileGist<Int2ObjectMap<PropertyIndexValue>> = GistManager.getInstance().newPsiFileGist("java.simple.property", 2, SimplePropertiesExternalizer()) { file ->
  findSimplePropertyCandidates(file.node.lighterAST)
}

private val allowedExpressions by lazy {
  TokenSet.create(ElementType.REFERENCE_EXPRESSION, ElementType.THIS_EXPRESSION, ElementType.SUPER_EXPRESSION, ElementType.PARENTH_EXPRESSION)
}

private fun findSimplePropertyCandidates(tree: LighterAST): Int2ObjectMap<PropertyIndexValue> {
  val result = Int2ObjectOpenHashMap<PropertyIndexValue>()

  object : RecursiveLighterASTNodeWalkingVisitor(tree) {
    var methodIndex = 0

    override fun visitNode(element: LighterASTNode) {
      if (JavaLightStubBuilder.isCodeBlockWithoutStubs(element)) return

      if (element.tokenType === JavaElementType.METHOD) {
        extractProperty(element)?.let {
          result.put(methodIndex, it)
        }
        methodIndex++
      }

      super.visitNode(element)
    }

    private fun extractProperty(method: LighterASTNode): PropertyIndexValue? {
      var isConstructor = true
      var isGetter = true

      var isBooleanReturnType = false
      var isVoidReturnType = false
      var setterParameterName: String? = null

      var refText: String? = null

      for (child in tree.getChildren(method)) {
        when (child.tokenType) {
          JavaElementType.TYPE -> {
            val children = tree.getChildren(child)
            if (children.size != 1) return null
            val typeElement = children[0]
            if (typeElement.tokenType == JavaTokenType.VOID_KEYWORD) isVoidReturnType = true
            if (typeElement.tokenType == JavaTokenType.BOOLEAN_KEYWORD) isBooleanReturnType = true
            isConstructor = false
          }
          JavaElementType.PARAMETER_LIST -> {
            if (isGetter) {
              if (LightTreeUtil.firstChildOfType(tree, child, JavaElementType.PARAMETER) != null) return null
            }
            else {
              val parameters = LightTreeUtil.getChildrenOfType(tree, child, JavaElementType.PARAMETER)
              if (parameters.size != 1) return null
              setterParameterName = JavaLightTreeUtil.getNameIdentifierText(tree, parameters[0])
              if (setterParameterName == null) return null
            }
          }
          JavaElementType.CODE_BLOCK -> {
            refText = if (isGetter) getGetterPropertyRefText(child) else getSetterPropertyRefText(child, setterParameterName!!)
            if (refText == null) return null
          }
          JavaTokenType.IDENTIFIER -> {
            if (isConstructor) return null
            val name = RecordUtil.intern(tree.charTable, child)
            when (PropertyUtilBase.getMethodNameGetterFlavour(name)) {
              PropertyUtilBase.GetterFlavour.NOT_A_GETTER -> {
                if (PropertyUtilBase.isSetterName(name)) {
                  isGetter = false
                }
                else {
                  return null
                }
              }
              PropertyUtilBase.GetterFlavour.BOOLEAN -> if (!isBooleanReturnType) return null
              else -> {
              }
            }
            if (isVoidReturnType && isGetter) return null
          }
        }
      }

      return refText?.let { PropertyIndexValue(it, isGetter) }
    }

    private fun getSetterPropertyRefText(codeBlock: LighterASTNode, setterParameterName: String): String? {
      val assignment = tree
        .getChildren(codeBlock)
        .singleOrNull { ElementType.JAVA_STATEMENT_BIT_SET.contains(it.tokenType) }
        ?.takeIf { it.tokenType == JavaElementType.EXPRESSION_STATEMENT }
        ?.let { LightTreeUtil.firstChildOfType(tree, it, JavaElementType.ASSIGNMENT_EXPRESSION) }
      if (assignment == null || LightTreeUtil.firstChildOfType(tree, assignment, JavaTokenType.EQ) == null) return null
      val operands = JavaLightTreeUtil.getExpressionChildren(tree, assignment)
      if (operands.size != 2) return null
      val unwrapped = JavaLightTreeUtil.skipParenthesesDown(tree, operands[1])
      if (unwrapped == null || LightTreeUtil.toFilteredString(tree, unwrapped, null) != setterParameterName) return null
      val lhsText = LightTreeUtil.toFilteredString(tree, operands[0], null)
      if (lhsText == setterParameterName) return null
      return lhsText
    }

    private fun getGetterPropertyRefText(codeBlock: LighterASTNode): String? {
      return tree
        .getChildren(codeBlock)
        .singleOrNull { ElementType.JAVA_STATEMENT_BIT_SET.contains(it.tokenType) }
        ?.takeIf { it.tokenType == JavaElementType.RETURN_STATEMENT }
        ?.let { LightTreeUtil.firstChildOfType(tree, it, allowedExpressions) }
        ?.takeIf(this::checkQualifiers)
        ?.let { LightTreeUtil.toFilteredString(tree, it, null) }
    }

    private fun checkQualifiers(expression: LighterASTNode): Boolean {
      if (!allowedExpressions.contains(expression.tokenType)) {
        return false
      }
      val qualifier = JavaLightTreeUtil.findExpressionChild(tree, expression)
      return qualifier == null || checkQualifiers(qualifier)
    }
  }.visitNode(tree.root)
  return result
}

@VisibleForTesting
data class PropertyIndexValue(val propertyRefText: String, val getter: Boolean)

private class SimplePropertiesExternalizer : DataExternalizer<Int2ObjectMap<PropertyIndexValue>> {
  override fun save(out: DataOutput, values: Int2ObjectMap<PropertyIndexValue>) {
    DataInputOutputUtil.writeINT(out, values.size)
    for (entry in Int2ObjectMaps.fastIterable(values)) {
      DataInputOutputUtil.writeINT(out, entry.intKey)
      EnumeratorStringDescriptor.INSTANCE.save(out, entry.value.propertyRefText)
      out.writeBoolean(entry.value.getter)
    }
  }

  override fun read(input: DataInput): Int2ObjectMap<PropertyIndexValue> {
    val size = DataInputOutputUtil.readINT(input)
    if (size == 0) {
      return Int2ObjectOpenHashMap()
    }

    val values = Int2ObjectOpenHashMap<PropertyIndexValue>(size)
    repeat(size) {
      val id = DataInputOutputUtil.readINT(input)
      val value = PropertyIndexValue(EnumeratorStringDescriptor.INSTANCE.read(input), input.readBoolean())
      values[id] = value
    }
    return values
  }
}