// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.treeView.smartTree.NodeProvider
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.Language
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Processor
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.lang.ref.SoftReference


abstract class StructureAwareNavBarModelExtension : AbstractNavBarModelExtension() {
  protected abstract val language: Language
  private var currentFile: SoftReference<PsiFile>? = null
  private var currentFileStructure: SoftReference<StructureViewModel>? = null
  private var currentFileModCount = -1L

  override fun getLeafElement(dataProvider: DataMap): PsiElement? {
    if (UISettings.getInstance().showMembersInNavigationBar) {
      val psiFile = dataProvider[CommonDataKeys.PSI_FILE]
      val editor = dataProvider[CommonDataKeys.EDITOR]
      if (editor == null
          || psiFile == null
          || !psiFile.isValid
          || !isAcceptableLanguage(psiFile)) {
        return null
      }
      val psiElement = psiFile.findElementAt(editor.caretModel.offset)
      if (isAcceptableLanguage(psiElement)) {
        try {
          buildStructureViewModel(psiFile, editor)?.let { model ->
            return (model.currentEditorElement as? PsiElement)?.originalElement
          }
        }
        catch (_: IndexNotReadyException) {
        }
      }
    }
    return null
  }

  protected open fun isAcceptableLanguage(psiElement: @Nullable PsiElement?): Boolean = psiElement?.language == language

  override fun processChildren(`object`: Any,
                               rootElement: Any?,
                               processor: Processor<Any>): Boolean {
    if (UISettings.getInstance().showMembersInNavigationBar) {
      (`object` as? PsiElement)?.let { psiElement ->
        if (isAcceptableLanguage(psiElement)) {
          buildStructureViewModel(psiElement.containingFile)?.let { model ->
            return processStructureViewChildren(model.root, `object`, processor)
          }
        }
      }
    }
    return super.processChildren(`object`, rootElement, processor)
  }

  override fun getParent(psiElement: PsiElement?): PsiElement? {
    if (isAcceptableLanguage(psiElement)) {
      val file = psiElement?.containingFile ?: return null
      if (psiElement == file) return null
      val model = buildStructureViewModel(file)
      if (model != null) {
        val parentInModel = findParentInModel(model.root, psiElement)
        if (acceptParentFromModel(parentInModel)) {
          return parentInModel
        }
      }
    }
    return super.getParent(psiElement)
  }

  protected open fun acceptParentFromModel(psiElement: PsiElement?): Boolean {
    return true
  }

  protected open fun findParentInModel(root: StructureViewTreeElement, psiElement: PsiElement): PsiElement? {
    for (child in childrenFromNodeAndProviders(root)) {
      if ((child as StructureViewTreeElement).value == psiElement) {
        return root.value as? PsiElement
      }
      findParentInModel(child, psiElement)?.let { return it }
    }
    return null
  }

  private fun buildStructureViewModel(file: PsiFile, editor: Editor? = null): StructureViewModel? {
    if (currentFile?.get() == file && currentFileModCount == file.modificationStamp) {
      if (editor == null) {
        currentFileStructure?.get()?.let { return it }
      }
      else {
        val editorStructure = editor.getUserData(MODEL)
        editorStructure?.get()?.let { return it }
      }
    }

    val model = createModel(file, editor)
    if (model != null) {
      currentFile = SoftReference(file)
      currentFileStructure = SoftReference(model)
      currentFileModCount = file.modificationStamp
      editor?.putUserData(MODEL, currentFileStructure)
    }
    return model
  }

  protected open fun createModel(file: PsiFile, editor: Editor?): @NotNull StructureViewModel? {
    val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(file)
    return (builder as? TreeBasedStructureViewBuilder)?.createStructureViewModel(editor)
  }

  private fun processStructureViewChildren(parent: StructureViewTreeElement,
                                           `object`: Any,
                                           processor: Processor<Any>): Boolean {
    if (parent.value == `object`) {
      return childrenFromNodeAndProviders(parent)
        .filterIsInstance<StructureViewTreeElement>()
        .all { processor.process(it.value) }
    }

    return childrenFromNodeAndProviders(parent)
      .filterIsInstance<StructureViewTreeElement>()
      .all { processStructureViewChildren(it, `object`, processor) }
  }

  protected open fun childrenFromNodeAndProviders(parent: StructureViewTreeElement): List<TreeElement> {
    val children = if (parent is PsiTreeElementBase<*>) parent.childrenWithoutCustomRegions else parent.children.toList()
    return children + applicableNodeProviders.flatMap { it.provideNodes(parent) }
  }

  override fun normalizeChildren(): Boolean = false

  protected open val applicableNodeProviders: List<NodeProvider<*>> = emptyList()

  companion object {
    val MODEL: Key<SoftReference<StructureViewModel>?> = Key.create("editor.structure.model")
  }
}