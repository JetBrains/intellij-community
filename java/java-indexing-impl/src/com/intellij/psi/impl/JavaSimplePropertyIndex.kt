// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.LighterASTNode
import com.intellij.lang.java.JavaParserDefinition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiField
import com.intellij.psi.impl.cache.RecordUtil
import com.intellij.psi.impl.source.JavaLightStubBuilder
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.impl.source.tree.ElementType
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.psi.stub.JavaStubImplUtil
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.EnumeratorStringDescriptor
import gnu.trove.TIntObjectHashMap
import java.io.DataInput
import java.io.DataOutput

private val indexId = ID.create<Int, TIntObjectHashMap<PropertyIndexValue>>("java.simple.property")
private object EmptyTIntObjectHashMap : TIntObjectHashMap<PropertyIndexValue>() {
  override fun put(key: Int, value: PropertyIndexValue?)= throw UnsupportedOperationException()
}

fun getFieldOfGetter(method: PsiMethodImpl): PsiField? = resolveFieldFromIndexValue(method, true)

fun getFieldOfSetter(method: PsiMethodImpl): PsiField? = resolveFieldFromIndexValue(method, false)

private fun resolveFieldFromIndexValue(method: PsiMethodImpl, isGetter: Boolean): PsiField? {
  val id = JavaStubImplUtil.getMethodStubIndex(method)
  if (id != -1) {
    val virtualFile = method.containingFile.virtualFile
    val data = FileBasedIndex.getInstance().getFileData(indexId, virtualFile, method.project)
    return data.values.firstOrNull()?.get(id)?.let { indexValue ->
      if (isGetter != indexValue.getter) return null
      val psiClass = method.containingClass
      val project = psiClass!!.project
      val expr = JavaPsiFacade.getElementFactory(project).createExpressionFromText(indexValue.propertyRefText, psiClass)
      return PropertyUtil.getSimplyReturnedField(expr)
    }
  }
  return null
}

data class PropertyIndexValue(val propertyRefText: String, val getter: Boolean)

class JavaSimplePropertyIndex : SingleEntryFileBasedIndexExtension<TIntObjectHashMap<PropertyIndexValue>>() {
  private val allowedExpressions by lazy {
    TokenSet.create(ElementType.REFERENCE_EXPRESSION, ElementType.THIS_EXPRESSION, ElementType.SUPER_EXPRESSION)
  }

  override fun getIndexer(): SingleEntryIndexer<TIntObjectHashMap<PropertyIndexValue>> = object : SingleEntryIndexer<TIntObjectHashMap<PropertyIndexValue>>(false) {
    override fun computeValue(inputData: FileContent): TIntObjectHashMap<PropertyIndexValue>? {
        var result: TIntObjectHashMap<PropertyIndexValue>? = null
        val tree = (inputData as PsiDependentFileContent).lighterAST

        object : RecursiveLighterASTNodeWalkingVisitor(tree) {
          var methodIndex = 0

          override fun visitNode(element: LighterASTNode) {
            if (JavaLightStubBuilder.isCodeBlockWithoutStubs(element)) return

            if (element.tokenType === JavaElementType.METHOD) {
              extractProperty(element)?.let {
                if (result == null) {
                  result = TIntObjectHashMap<PropertyIndexValue>()
                }
                result!!.put(methodIndex, it)
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
                  } else {
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
                  val flavour = PropertyUtil.getMethodNameGetterFlavour(name)
                  when (flavour) {
                    PropertyUtilBase.GetterFlavour.NOT_A_GETTER -> {
                      if (PropertyUtil.isSetterName(name)) {
                        isGetter = false
                      }
                      else {
                        return null
                      }
                    }
                    PropertyUtilBase.GetterFlavour.BOOLEAN -> if (!isBooleanReturnType) return null
                    else -> { }
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
            val operands = LightTreeUtil.getChildrenOfType(tree, assignment, ElementType.EXPRESSION_BIT_SET)
            if (operands.size != 2 || LightTreeUtil.toFilteredString(tree, operands[1], null) != setterParameterName) return null
            val lhsText = LightTreeUtil.toFilteredString(tree, operands[0], null)
            if (lhsText == setterParameterName) return null
            return lhsText
          }

          private fun getGetterPropertyRefText(codeBlock: LighterASTNode): String? {
            return tree
              .getChildren(codeBlock)
              .singleOrNull { ElementType.JAVA_STATEMENT_BIT_SET.contains(it.tokenType) }
              ?.takeIf { it.tokenType == JavaElementType.RETURN_STATEMENT}
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
  }

  override fun getValueExternalizer(): DataExternalizer<TIntObjectHashMap<PropertyIndexValue>> = object: DataExternalizer<TIntObjectHashMap<PropertyIndexValue>> {
    override fun save(out: DataOutput, values: TIntObjectHashMap<PropertyIndexValue>) {
      DataInputOutputUtil.writeINT(out, values.size())
      values.forEachEntry { id, value ->
        DataInputOutputUtil.writeINT(out, id)
        EnumeratorStringDescriptor.INSTANCE.save(out, value.propertyRefText)
        out.writeBoolean(value.getter)
        return@forEachEntry true
      }
    }

    override fun read(input: DataInput): TIntObjectHashMap<PropertyIndexValue> {
      val size = DataInputOutputUtil.readINT(input)
      if (size == 0) return EmptyTIntObjectHashMap
      val values = TIntObjectHashMap<PropertyIndexValue>(size)
      repeat(size) {
        val id = DataInputOutputUtil.readINT(input)
        val value = PropertyIndexValue(EnumeratorStringDescriptor.INSTANCE.read(input), input.readBoolean())
        values.put(id, value)
      }
      return values
    }
  }

  override fun getName(): ID<Int, TIntObjectHashMap<PropertyIndexValue>> = indexId

  override fun getInputFilter(): FileBasedIndex.InputFilter = object : DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE) {
    override fun acceptInput(file: VirtualFile): Boolean = JavaParserDefinition.JAVA_FILE.shouldBuildStubFor(file)
  }

  override fun getVersion(): Int = 3
}