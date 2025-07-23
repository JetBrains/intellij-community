// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.ide.actions.GotoClassPresentationUpdater.getActionTitlePluralized
import com.intellij.ide.actions.GotoClassPresentationUpdater.getTabTitlePluralized
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersStatisticsCollector.LangFilterCollector
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.gotoByName.*
import com.intellij.ide.util.gotoByName.LanguageRef.Companion.forAllLanguages
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.NameUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.regex.Pattern

private val patternToDetectMembers = Pattern.compile("(.+)(#)(.*)")

open class ClassSearchEverywhereContributor @Internal constructor(event: AnActionEvent, contributorModules: List<SearchEverywhereContributorModule>?)
  : AbstractGotoSEContributor(event, contributorModules), EssentialContributor, SearchEverywherePreviewProvider {
  private val filter = createLanguageFilter(event.getRequiredData(CommonDataKeys.PROJECT))

  constructor(event: AnActionEvent) : this(event, null)

  companion object {
    @JvmStatic
    fun createLanguageFilter(project: Project): PersistentSearchEverywhereContributorFilter<LanguageRef> {
      val items = forAllLanguages()
      val persistentConfig = GotoClassSymbolConfiguration.getInstance(project)
      return PersistentSearchEverywhereContributorFilter(items, persistentConfig, LanguageRef::displayName, LanguageRef::icon)
    }
  }

  override fun getGroupName(): @Nls String = getTabTitlePluralized()

  override fun getFullGroupName(): String = getActionTitlePluralized().joinToString("/")

  override fun getSortWeight(): Int = 100

  override fun createModel(project: Project): FilteringGotoByModel<LanguageRef> {
    val model = GotoClassModel2(project)
    model.setFilterItems(filter.selectedElements)
    return model
  }

  override fun getActions(onChanged: Runnable): List<AnAction> {
    return doGetActions(filter = filter, statisticsCollector = LangFilterCollector(), onChanged = onChanged)
  }

  override fun filterControlSymbols(pattern: String): String {
    var effectivePattern = pattern
    if (effectivePattern.contains('#')) {
      effectivePattern = applyPatternFilter(effectivePattern, patternToDetectMembers)
    }
    if (effectivePattern.contains('$')) {
      effectivePattern = applyPatternFilter(effectivePattern, ChooseByNamePopup.patternToDetectAnonymousClasses)
    }

    return super.filterControlSymbols(effectivePattern)
  }

  override fun isEmptyPatternSupported(): Boolean = true

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getElementPriority(element: Any, searchPattern: String): Int {
    return super.getElementPriority(element, searchPattern) + 5
  }

  override fun createExtendedInfo(): ExtendedInfo? = createPsiExtendedInfo().let {
    contributorModules?.firstNotNullOfOrNull { mod -> mod.mixinExtendedInfo(it) } ?: it
  }

  override suspend fun createSourceNavigationRequest(element: PsiElement, file: VirtualFile, searchText: String): NavigationRequest? {
    val memberName = getMemberName(searchText)
    if (memberName != null) {
      readAction {
        findMember(memberPattern = memberName, fullPattern = searchText, psiElement = element, file = file)?.navigationRequest()
      }?.let {
        return it
      }
    }

    return super.createSourceNavigationRequest(element, file, searchText)
  }

  @Internal
  class Factory : SearchEverywhereContributorFactory<Any?> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any?> {
      return PSIPresentationBgRendererWrapper.wrapIfNecessary(ClassSearchEverywhereContributor(initEvent))
    }
  }
}

private fun findMember(memberPattern: String, fullPattern: String, psiElement: PsiElement, file: VirtualFile): Navigatable? {
  val factory = LanguageStructureViewBuilder.getInstance().forLanguage(psiElement.language)
  val builder = factory?.getStructureViewBuilder(psiElement.containingFile) ?: return null
  val editors = FileEditorManager.getInstance(psiElement.project).getEditorList(file)
  if (editors.isEmpty()) {
    return null
  }

  val view = builder.createStructureView(editors[0], psiElement.project)
  try {
    val element = findElement(view.treeModel.root, psiElement, 4) ?: return null
    val matcher = NameUtil.buildMatcher(memberPattern).build()
    var max = Int.MIN_VALUE
    var target: Any? = null
    for (treeElement in element.children) {
      if (treeElement is StructureViewTreeElement) {
        val value = treeElement.value
        if (value is PsiElement && value is Navigatable && fullPattern == CopyReferenceAction.elementToFqn(value)) {
          return value
        }

        val presentableText = treeElement.getPresentation().presentableText
        if (presentableText != null) {
          val degree = matcher.matchingDegree(presentableText)
          if (degree > max) {
            max = degree
            target = treeElement.value
          }
        }
      }
    }
    return target as? Navigatable
  }
  finally {
    Disposer.dispose(view)
  }
}

private fun findElement(node: StructureViewTreeElement, element: PsiElement, hopes: Int): StructureViewTreeElement? {
  val value = node.value as? PsiElement ?: return null
  if (value.isEquivalentTo(element)) {
    return node
  }

  if (hopes != 0) {
    for (child in node.children) {
      if (child is StructureViewTreeElement) {
        findElement(child, element, hopes - 1)?.let {
          return it
        }
      }
    }
  }
  return null
}

private fun getMemberName(searchedText: String): String? {
  val index = searchedText.lastIndexOf('#')
  return if (index == -1) null else searchedText.substring(index + 1).trim().ifEmpty { null }
}