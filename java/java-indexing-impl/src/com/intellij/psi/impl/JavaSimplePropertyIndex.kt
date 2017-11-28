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
package com.intellij.psi.impl

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.LighterASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiField
import com.intellij.psi.impl.cache.RecordUtil
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes
import com.intellij.psi.impl.source.JavaLightStubBuilder
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.impl.source.tree.ElementType
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stub.JavaStubImplUtil
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

private val indexId = ID.create<Int, PropertyIndexValue>("java.simple.property")
private val log = Logger.getInstance(JavaSimplePropertyIndex::class.java)

fun getFieldOfGetter(method: PsiMethodImpl): PsiField? = resolveFieldFromIndexValue(method, true)

fun getFieldOfSetter(method: PsiMethodImpl): PsiField? = resolveFieldFromIndexValue(method, false)

private fun resolveFieldFromIndexValue(method: PsiMethodImpl, isGetter: Boolean): PsiField? {
  val id = JavaStubImplUtil.getMethodStubIndex(method)
  if (id != -1) {
    val values = FileBasedIndex.getInstance().getValues(indexId, id, GlobalSearchScope.fileScope(method.containingFile))
    when (values.size) {
      0 -> return null
      1 -> {
        val indexValue = values[0]
        if (isGetter != indexValue.getter) return null
        val psiClass = method.containingClass
        val project = psiClass!!.project
        val expr = JavaPsiFacade.getElementFactory(project).createExpressionFromText(indexValue.propertyRefText, psiClass)
        return PropertyUtil.getSimplyReturnedField(expr)
      }
      else -> {
        log.error("multiple index values for method $method")
      }
    }
  }
  return null
}

data class PropertyIndexValue(val propertyRefText: String, val getter: Boolean)

class JavaSimplePropertyIndex : FileBasedIndexExtension<Int, PropertyIndexValue>(), PsiDependentIndex {
  override fun getIndexer(): DataIndexer<Int, PropertyIndexValue, FileContent> = DataIndexer { inputData ->
    val result = ContainerUtil.newHashMap<Int, PropertyIndexValue>()
    val tree = (inputData as FileContentImpl).lighterASTForPsiDependentIndex

    object : RecursiveLighterASTNodeWalkingVisitor(tree) {
      var methodIndex = 0

      override fun visitNode(element: LighterASTNode) {
        if (JavaLightStubBuilder.isCodeBlockWithoutStubs(element)) return

        if (element.tokenType === JavaElementType.METHOD) {
          extractProperty(element)?.let { result.put(methodIndex, it) }
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

      private fun getGetterPropertyRefText(codeBlock: LighterASTNode): String? = tree
        .getChildren(codeBlock)
        .singleOrNull { ElementType.JAVA_STATEMENT_BIT_SET.contains(it.tokenType) }
        ?.takeIf { it.tokenType == JavaElementType.RETURN_STATEMENT}
        ?.let { LightTreeUtil.firstChildOfType(tree, it, ElementType.EXPRESSION_BIT_SET) }
        ?.let { LightTreeUtil.toFilteredString(tree, it, null) }

    }.visitNode(tree.root)
    result
  }

  override fun getKeyDescriptor(): KeyDescriptor<Int> = EnumeratorIntegerDescriptor.INSTANCE

  override fun getValueExternalizer(): DataExternalizer<PropertyIndexValue> = object: DataExternalizer<PropertyIndexValue> {
    override fun save(out: DataOutput, value: PropertyIndexValue?) {
      value!!
      EnumeratorStringDescriptor.INSTANCE.save(out, value.propertyRefText)
      out.writeBoolean(value.getter)
    }

    override fun read(input: DataInput): PropertyIndexValue = PropertyIndexValue(EnumeratorStringDescriptor.INSTANCE.read(input), input.readBoolean())
  }

  override fun getName(): ID<Int, PropertyIndexValue> = indexId

  override fun getInputFilter(): FileBasedIndex.InputFilter = object : DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE) {
    override fun acceptInput(file: VirtualFile): Boolean = JavaStubElementTypes.JAVA_FILE.shouldBuildStubFor(file)
  }

  override fun dependsOnFileContent(): Boolean = true

  override fun getVersion(): Int = 0
}