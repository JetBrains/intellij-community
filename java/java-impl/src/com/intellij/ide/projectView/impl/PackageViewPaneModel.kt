// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.history.LocalHistory
import com.intellij.ide.DeleteProvider
import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectViewPane.extractValueFromNode
import com.intellij.ide.projectView.impl.nodes.PackageElement
import com.intellij.ide.projectView.impl.nodes.PackageElementNode
import com.intellij.ide.projectView.impl.nodes.PackageViewProjectNode
import com.intellij.ide.util.DeleteHandler
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.java.JavaBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.module.isQualifiedModuleNamesEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.projectView.impl.DefaultTreeStructurePsiExtractor
import com.intellij.platform.projectView.impl.ProjectViewPsiExtractor
import com.intellij.platform.projectView.impl.TreeStructureBasedProjectViewPaneModel
import com.intellij.platform.projectView.impl.TreeStructureProjectViewNode
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Collections

internal class PackageViewPaneModelProvider : ProjectViewPaneProvider {
  override fun createPanes(project: Project): Flow<List<ProjectViewPaneModel>> {
    return flowOf(listOf(PackageViewPaneModel(project)))
  }
}

internal class PackageViewPaneModel(project: Project) : TreeStructureBasedProjectViewPaneModel(project) {
  override suspend fun id(): ProjectViewPaneId = projectViewPaneId("PackagesPane")

  override suspend fun presentableName(): @NlsSafe String = JavaBundle.message("title.packages")

  override suspend fun order(): Int = 1

  override val psi: ProjectViewPsiExtractor<TreeStructureProjectViewNode> = PackageViewPsiExtractor(project)
  
  private val packageDeleteProvider = PackageDeleteProvider()

  override fun createTreeStructure(viewSettings: ViewSettings): AbstractProjectTreeStructure {
    return PackageTreeStructure(project, viewSettings)
  }

  override fun supportsOption(option: ProjectViewPaneOption): Boolean {
    return when (option) {
      is ProjectViewPaneOption.FlattenModules -> {
        PlatformUtils.isIntelliJ() &&
        isQualifiedModuleNamesEnabled(project) &&
        ProjectViewPaneSettingsService.getInstance(project).isOptionSelected(ProjectViewPaneOption.ShowModules)
      }
      is ProjectViewPaneOption.ShowLibraryContents -> true
      is ProjectViewPaneOption.ShowModules -> PlatformUtils.isIntelliJ()
      else -> super.supportsOption(option)
    }
  }

  override fun uiDataSnapshotForSelection(
    selectedNodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>,
    sink: DataSink,
    snapshot: DataSnapshot?,
  ) {
    super.uiDataSnapshotForSelection(selectedNodes, sink, snapshot)
    val selectedNode = selectedNodes.singleOrNull()
    sink.lazy(PlatformDataKeys.DELETE_ELEMENT_PROVIDER) {
      val value = selectedNode?.userObject?.elementDescriptor?.let { extractValueFromNode(it) }
      if (value is PackageElement) packageDeleteProvider else null
    }
    sink.lazy(PackageElement.DATA_KEY) {
      selectedNode?.userObject?.elementDescriptor?.let { extractValueFromNode(it) } as? PackageElement?
    }
    sink.lazy(PlatformCoreDataKeys.MODULE) {
      (selectedNode?.userObject?.elementDescriptor?.let { extractValueFromNode(it) } as? PackageElement?)?.module
    }
  }

  private inner class PackageDeleteProvider : DeleteProvider {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    @Suppress("UNCHECKED_CAST") // we know for sure it's BackendProjectViewNodeModel<TreeStructureProjectViewNode>
    override fun canDeleteElement(dataContext: DataContext): Boolean {
      val objs = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataContext)?.filterIsInstance<BackendProjectViewNodeModel<*>>()
      if (!objs.isNullOrEmpty()) {
        for (directory in psi.extractPsiDirectories(objs as List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>)) {
          if (!directory.getManager().isInProject(directory)) return false
        }
      }
      return true
    }

    @Suppress("UNCHECKED_CAST") // we know for sure it's BackendProjectViewNodeModel<TreeStructureProjectViewNode>
    override fun deleteElement(dataContext: DataContext) {
      val objs = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataContext)?.filterIsInstance<BackendProjectViewNodeModel<*>>()
      val allElements = if (objs != null) {
        psi.extractPsiDirectories(objs as List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>)
      }
      else {
        emptyList()
      }
      val validElements: MutableList<PsiElement> = mutableListOf()
      for (psiElement in allElements) {
        if (psiElement.isValid()) validElements.add(psiElement)
      }
      val elements = PsiUtilCore.toPsiElementArray(validElements)

      val a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"))
      try {
        DeleteHandler.deletePsiElement(elements, project)
      }
      finally {
        a.finish()
      }
    }
  }
}

private class PackageTreeStructure(project: Project, viewSettings: ViewSettings) : AbstractProjectTreeStructure(project, viewSettings) {
  override fun createRoot(
    project: Project,
    settings: ViewSettings,
  ): AbstractTreeNode<*> {
    return PackageViewProjectNode(project, viewSettings)
  }
}

private class PackageViewPsiExtractor(project: Project) : DefaultTreeStructurePsiExtractor(project) {
  override fun extractPsiElements(node: BackendProjectViewNodeModel<TreeStructureProjectViewNode>): List<PsiElement> {
    val legacyUserObject = node.userObject.elementDescriptor
    val value = extractValueFromNode(legacyUserObject)
    if (value is PackageElement) {
      return listOfNotNull(value.`package`.takeIf { it.isValid })
    }
    return super.extractPsiElements(node)
  }

  override fun extractPsiDirectories(nodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>): List<PsiDirectory> {
    val directories: MutableList<PsiDirectory> = mutableListOf()
    for (node in nodes) {
      val userObject = node.userObject.elementDescriptor as? PackageElementNode ?: continue
      val packageElement = userObject.getValue()
      var aPackage = packageElement?.getPackage() ?: continue
      val module = packageElement.module ?: continue
      val scope = GlobalSearchScope.moduleScope(module)
      Collections.addAll<PsiDirectory?>(directories, *aPackage.getDirectories(scope))
      if (Registry.`is`("projectView.choose.directory.on.compacted.middle.packages")) {
        val parentValue = userObject.parent.getValue()
        val parentNodePackage = if (parentValue is PackageElement) parentValue.getPackage() else null
        while (true) {
          val parentPackage = aPackage.getParentPackage()
          if (parentPackage == null || parentPackage.getQualifiedName().isEmpty() || parentPackage == parentNodePackage) {
            break
          }
          aPackage = parentPackage
          directories += aPackage.getDirectories(scope)
        }
      }
    }
    if (directories.isNotEmpty()) {
      return directories
    }
    return super.extractPsiDirectories(nodes)
  }
}
