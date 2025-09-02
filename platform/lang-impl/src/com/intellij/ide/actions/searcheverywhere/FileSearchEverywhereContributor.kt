// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersStatisticsCollector.FileTypeFilterCollector
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.gotoByName.FileTypeRef.Companion.forAllFileTypes
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoFileConfiguration
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.DirtyUI
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.function.Function
import javax.swing.JList
import javax.swing.ListCellRenderer

private val LOG = Logger.getInstance(FileSearchEverywhereContributor::class.java)

open class FileSearchEverywhereContributor(event: AnActionEvent, contributorModules: List<SearchEverywhereContributorModule>?) : AbstractGotoSEContributor(
  event, contributorModules), EssentialContributor, SearchEverywherePreviewProvider {
  private val modelForRenderer: GotoFileModel
  private val filter: PersistentSearchEverywhereContributorFilter<FileTypeRef>

  constructor(event: AnActionEvent) : this(event, null)

  init {
    val project = event.getRequiredData(CommonDataKeys.PROJECT)
    modelForRenderer = GotoFileModel(project)
    filter = createFileTypeFilter(project)
  }

  companion object {
    @JvmStatic
    fun createFileTypeFilter(project: Project): PersistentSearchEverywhereContributorFilter<FileTypeRef> {
      val items = forAllFileTypes().toMutableList()
      items.add(0, GotoFileModel.DIRECTORY_FILE_TYPE_REF)
      return PersistentSearchEverywhereContributorFilter(items, GotoFileConfiguration.getInstance(project), FileTypeRef::displayName,
                                                         FileTypeRef::icon)
    }
  }

  override fun getGroupName(): String = IdeBundle.message("search.everywhere.group.name.files")

  override fun getSortWeight(): Int = 200

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getElementPriority(element: Any, searchPattern: String): Int = super.getElementPriority(element, searchPattern) + 2

  override fun createModel(project: Project): FilteringGotoByModel<FileTypeRef> {
    val model = GotoFileModel(project)
    model.setFilterItems(filter.selectedElements)
    return model
  }

  override fun getActions(onChanged: Runnable): List<AnAction> = doGetActions(filter, FileTypeFilterCollector(), onChanged)

  final override fun getElementsRenderer(): ListCellRenderer<in Any?> {
    return object : SearchEverywherePsiRenderer(this) {
      @DirtyUI
      override fun getItemMatchers(list: JList<*>, value: Any): ItemMatchers {
        return getNonComponentItemMatchers({ v -> super.getItemMatchers(list, v) }, value)
      }

      override fun getNonComponentItemMatchers(matcherProvider: Function<Any, ItemMatchers>, value: Any): ItemMatchers {
        val defaultMatchers = matcherProvider.apply(value)
        if (value !is PsiFileSystemItem) {
          return defaultMatchers
        }
        return GotoFileModel.convertToFileItemMatchers(defaultMatchers, value, modelForRenderer)
      }
    }
  }

  final override fun processElement(
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>,
    model: FilteringGotoByModel<*>,
    element: Any?,
    degree: Int,
  ): Boolean {
    if (progressIndicator.isCanceled) {
      return false
    }

    if (element == null) {
      LOG.error("Null returned from $model in ${javaClass.simpleName}")
      return true
    }

    return consumer.process(FoundItemDescriptor(element, degree))
  }

  override suspend fun createSourceNavigationRequest(element: PsiElement, file: VirtualFile, searchText: String): NavigationRequest? {
    val navigationRequests = serviceAsync<NavigationRequests>()
    return readAction {
      navigationRequests.sourceNavigationRequest(project = project, file = file, offset = -1, elementRange = null)
    }
  }

  final override suspend fun triggerLineOrColumnFeatureUsed(extendedNavigatable: Navigatable) {
    serviceAsync<FeatureUsageTracker>().triggerFeatureUsed("navigation.goto.file.line")
  }

  override fun getDataForItem(element: Any, dataId: String): Any? {
    if (CommonDataKeys.PSI_FILE.`is`(dataId) && element is PsiFile) {
      return element
    }
    return super.getDataForItem(element, dataId)
  }

  override fun getItemDescription(element: Any): String? {
    if ((element is PsiFile || element is PsiDirectory) && (element as PsiFileSystemItem).isValid) {
      var path: String? = FileUtilRt.toSystemIndependentName(element.virtualFile.path)
      myProject.basePath?.let {
        path = FileUtilRt.getRelativePath(it, path!!, '/')
      }
      return path
    }
    return super.getItemDescription(element)
  }

  override fun isEmptyPatternSupported(): Boolean = true

  override fun createExtendedInfo(): @Nls ExtendedInfo? = createPsiExtendedInfo()
}

@Internal
class FileSearchEverywhereContributorFactory : SearchEverywhereContributorFactory<Any?> {
  override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any?> {
    return PSIPresentationBgRendererWrapper.wrapIfNecessary(FileSearchEverywhereContributor(initEvent))
  }
}