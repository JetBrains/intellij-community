// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.RootContainer.Companion.merge
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkContext
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.*
import com.intellij.openapi.roots.libraries.LibraryContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.*
import com.intellij.util.containers.SmartHashSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class RootContainerScope internal constructor(
  internal val rootContainer: RootContainer,
  protected val allModules: Set<Module>,
  private val containsLibraries: Boolean,
  project: Project,
) : GlobalSearchScope(project),
    CodeInsightContextAwareSearchScope,
    ActualCodeInsightContextInfo,
    UnionCapableScope {

  private val mySingleFileSourcesTracker: SingleFileSourcesTracker = SingleFileSourcesTracker.getInstance(project)

  protected val myProjectFileIndex: ProjectFileIndexImpl = ProjectRootManager.getInstance(project).getFileIndex() as ProjectFileIndexImpl

  internal abstract val mainModules: Collection<Module>

  override fun contains(file: VirtualFile): Boolean {
    // in case of single file source
    if (mainModules.any { mySingleFileSourcesTracker.isSourceDirectoryInModule(file, it) }) {
      return true
    }

    if (this.isSharedSourceSupportEnabled) {
      val roots = myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file)
      return roots.any { root -> rootContainer.getRootDescriptor(root) != null }
    }
    else {
      val root = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file)
      return root != null && rootContainer.containsRoot(root)
    }
  }

  @ApiStatus.Experimental
  override fun contains(file: VirtualFile, context: CodeInsightContext): Boolean {
    if (!this.isSharedSourceSupportEnabled) {
      return contains(file)
    }

    // in case of single file source
    if (context is ModuleContext) {
      if (mainModules.any {
          context.getModule() === it && mySingleFileSourcesTracker.isSourceDirectoryInModule(file, it)
        }) {
        // todo IJPL-339 is it correct???
        return true
      }
    }

    val rootDescriptor = convertContextToRootDescriptor(file, context) ?: return false
    val root = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file) ?: return false
    val existingDescriptor = rootContainer.getRootDescriptor(root) ?: return false
    val result = existingDescriptor.correspondTo(rootDescriptor)

    // don't change order of checks!
    if (!result && LOG.isDebugEnabled() && contains(file)) {
      LOG.debug("File $file is in scope, but not with $context")
    }

    return result
  }

  private val isSharedSourceSupportEnabled: Boolean
    get() = isSharedSourceSupportEnabled(project)

  @get:ApiStatus.Experimental
  override val codeInsightContextInfo: CodeInsightContextInfo
    get() = this

  @ApiStatus.Experimental
  override fun getFileInfo(file: VirtualFile): CodeInsightContextFileInfo {
    //in case of single file source
    if (mainModules.any { mySingleFileSourcesTracker.isSourceDirectoryInModule(file, it) }) {
      // todo IJPL-339 support bazel in search scopes???
      return NoContextFileInfo()
    }

    val roots = myProjectFileIndex.getModuleSourceOrLibraryClassesRoots(file)
    if (roots.isEmpty()) return DoesNotContainFileInfo()

    val result = SmartHashSet<CodeInsightContext>()
    for (rootDescriptor in roots) {
      val descriptor = rootContainer.getRootDescriptor(rootDescriptor)
      if (descriptor != null) {
        val context = convertToContext(descriptor)
        if (context != null) {
          result.add(context)
        }
      }
    }
    return createContainingContextFileInfo(result)
  }

  private fun convertToContext(descriptor: ScopeRootDescriptor): CodeInsightContext? {
    val entry = descriptor.orderEntry
    if (entry is ModuleSourceOrderEntry) {
      val module = entry.getRootModel().getModule()
      val bridge = ProjectModelContextBridge.getInstance(project)
      return bridge.getContext(module)
    }

    if (entry is LibraryOrderEntry) {
      val library = entry.getLibrary() ?: return null
      val bridge = ProjectModelContextBridge.getInstance(project)
      return bridge.getContext(library)
    }

    if (entry is JdkOrderEntry) {
      val sdk = entry.getJdk() ?: return null
      val bridge = ProjectModelContextBridge.getInstance(project)
      return bridge.getContext(sdk)
    }

    return null
  }

  private fun convertContextToRootDescriptor(
    root: VirtualFile,
    context: CodeInsightContext,
  ): RootDescriptor? {
    if (context is ModuleContext) {
      val module = context.getModule()
      if (module == null) return null
      return ModuleRootDescriptor(root, module)
    }

    if (context is LibraryContext) {
      val library = context.getLibrary()
      if (library == null) return null
      return LibraryRootDescriptor(root, library)
    }

    if (context is SdkContext) {
      val sdk = context.getSdk()
      if (sdk == null) return null
      return SdkRootDescriptor(root, sdk)
    }

    return null
  }

  override fun isSearchInModuleContent(aModule: Module): Boolean =
    aModule in allModules

  override fun isSearchInLibraries(): Boolean =
    containsLibraries

  override fun uniteWith(scopes: Collection<GlobalSearchScope>): UnionCapableScope.UnionResult? {
    val (myBros, notMyBros) = scopes.partition { it is RootContainerScope }

    if (myBros.isEmpty()) return null

    val rootContainerScopes = (myBros + this) as List<RootContainerScope>

    val mergedContainer = rootContainerScopes.map { it.rootContainer }.merge()
    val allModules by lazy {
      rootContainerScopes.flatMap { it.allModules }.toSet()
    }

    val containsLibraries = rootContainerScopes.any { it.containsLibraries }

    val united = ModuleWithDepUnion(
      rootContainer = mergedContainer,
      mainModules = rootContainerScopes.flatMapTo(HashSet()) { it.mainModules },
      allModules = allModules,
      containsLibraries = containsLibraries,
      project = project
    )
    return UnionCapableScope.UnionResult(united, notMyBros.toSet())
  }

  override fun getProject(): Project = requireNotNull(super.getProject())
}

private val LOG = logger<RootContainerScope>()