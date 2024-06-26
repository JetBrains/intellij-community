// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.OpenInRightSplitAction.Companion.openInRightSplit
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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.DirtyUI
import com.intellij.util.Processor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import javax.swing.JList
import javax.swing.ListCellRenderer

private val LOG = Logger.getInstance(FileSearchEverywhereContributor::class.java)

open class FileSearchEverywhereContributor(event: AnActionEvent) : AbstractGotoSEContributor(
  event), EssentialContributor, SearchEverywherePreviewProvider {
  private val modelForRenderer: GotoFileModel
  private val filter: PersistentSearchEverywhereContributorFilter<FileTypeRef>

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

  override fun getElementsRenderer(): ListCellRenderer<Any> {
    return object : SearchEverywherePsiRenderer(this) {
      @DirtyUI
      override fun getItemMatchers(list: JList<*>, value: Any): ItemMatchers {
        val defaultMatchers = super.getItemMatchers(list, value)
        if (value !is PsiFileSystemItem) {
          return defaultMatchers
        }
        return GotoFileModel.convertToFileItemMatchers(defaultMatchers, value, modelForRenderer)
      }
    }
  }

  override fun processElement(
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>?>,
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

  override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean {
    if (selected is PsiFile) {
      val file = selected.virtualFile
      if (file == null || myProject == null) {
        return super.processSelectedItem(selected, modifiers, searchText)
      }

      val lineAndColumn = getLineAndColumn(searchText)
      val descriptor = OpenFileDescriptor(myProject, file, lineAndColumn.first, lineAndColumn.second)
      if (descriptor.canNavigate()) {
        myProject.service<FileSearchEverywhereContributorCoroutineScopeHolder>().coroutineScope.launch {
          withContext(Dispatchers.EDT) {
            @Suppress("DEPRECATION")
            if ((modifiers and InputEvent.SHIFT_MASK) != 0) {
              openInRightSplit(project = myProject, file = file, element = descriptor, requestFocus = true)
            }
            else {
              descriptor.navigate(true)
            }
          }
          if (lineAndColumn.first > 0) {
            serviceAsync<FeatureUsageTracker>().triggerFeatureUsed("navigation.goto.file.line")
          }
        }
        return true
      }
    }

    return super.processSelectedItem(selected, modifiers, searchText)
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
      if (myProject != null) {
        myProject.basePath?.let {
          path = FileUtilRt.getRelativePath(it, path!!, '/')
        }
      }
      return path
    }
    return super.getItemDescription(element)
  }

  override fun isEmptyPatternSupported(): Boolean = true

  override fun createExtendedInfo(): @Nls ExtendedInfo? = createPsiExtendedInfo()
}

@Service(Service.Level.PROJECT)
private class FileSearchEverywhereContributorCoroutineScopeHolder(coroutineScope: CoroutineScope) {
  @JvmField val coroutineScope: CoroutineScope = coroutineScope.childScope("FileSearchEverywhereContributor")
}

@Internal
class FileSearchEverywhereContributorFactory : SearchEverywhereContributorFactory<Any?> {
  override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any?> {
    return PSIPresentationBgRendererWrapper.wrapIfNecessary(FileSearchEverywhereContributor(initEvent))
  }
}