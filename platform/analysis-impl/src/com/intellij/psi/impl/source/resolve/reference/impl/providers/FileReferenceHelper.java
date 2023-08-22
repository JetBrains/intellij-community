/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileContextProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * This extension point allows codeinsight features (e.g., "Goto Declaration") to work for custom file references
 * by defining their root, their resolving mechanism and quickfixes to repair broken urls.
 * For example, {@link com.intellij.codeInsight.daemon.impl.analysis.HtmlFileReferenceHelper} defines resolving mechanism for
 * relative "href=" references based on the current module content root.
 *
 * @see FileContextProvider
 * @see FileReferenceResolver
 * @see CustomizableReferenceProvider
 */
public abstract class FileReferenceHelper {

  public static final ExtensionPointName<FileReferenceHelper> EP_NAME = new ExtensionPointName<>("com.intellij.psi.fileReferenceHelper");

  /**
   * The extended way to add contexts to file references.
   * It adds more control, since:
   * <li>Provides information about the context (not only the host file)
   * <li>Adds information about the stage e.g. for "bind" operation the context list can be slightly different
   * <li>Gives a way to stop adding contexts, if some helper want to override the context completely.
   *
   * @return true, if we need to continue adding contexts for helpers, false otherwise
   * @see #getContexts(Project, VirtualFile) for simple context providing
   * @apiNote before running this method {@link #isMine(Project, VirtualFile)} should be called
   */
  @ApiStatus.Experimental
  public boolean processContexts(@NotNull FileReferenceSetParameters parameters,
                                 @NotNull final VirtualFile hostFile,
                                 boolean bind,
                                 @NotNull Processor<? super PsiFileSystemItem> processor) {
    PsiElement element = parameters.getElement();
    getContexts(element.getProject(), hostFile).forEach(processor::process);
    return true;
  }

  /**
   * This method is called if {@link FileReferenceSet#isAbsolutePathReference()} returns <b>true</b> e.g. for absolute references only
   *
   * @return roots, which could be used as the start resolution context.
   */
  @NotNull
  public Collection<PsiFileSystemItem> getRoots(@NotNull Module module, @NotNull VirtualFile hostFile) {
    return getRoots(module);
  }

  /**
   * @return true, if the helper can be applied
   */
  public abstract boolean isMine(@NotNull Project project, @NotNull final VirtualFile hostFile);

  /**
   * @return root which should be used as the context while refactor.
   * Used only for {@link FileReferenceSet#isAbsolutePathReference()} references.
   */
  @Nullable
  public PsiFileSystemItem findRoot(@NotNull Project project, @NotNull final VirtualFile dstVFile) {
    return null;
  }

  /**
   * This method is called if {@link FileReferenceSet#isAbsolutePathReference()} returns <b>false</b> e.g. for relative references only
   *
   * @return roots, which could be used as the start resolution context.
   * @see #processContexts(FileReferenceSetParameters, VirtualFile, boolean, Processor) for more generic processing
   * @apiNote before running this method {@link #isMine(Project, VirtualFile)} should be called
   */
  @NotNull
  public Collection<PsiFileSystemItem> getContexts(@NotNull Project project, @NotNull final VirtualFile hostFile) {
    return emptySet();
  }

  /**
   * The method is called inside the bind operation.
   * Since the operation involves two files, we've added a way to customize the behaviour based on the information
   * @return true, if the helper can be applied
   */
  @ApiStatus.Experimental
  public boolean isMine(@NotNull Project project,
                        @NotNull VirtualFile contextFile,
                        @NotNull VirtualFile referencedFile) {
    //not sure about what's more correct file here, I'd say that it should be contextFile instead of referencedFile
    //but for backward compatibility let's keep referencedFile
    return isMine(project, referencedFile);
  }

  /**
   * Similar to {@link #processContexts)} provides a more generic way to work with contexts.
   */
  @ApiStatus.Experimental
  public boolean processTargetContexts(@NotNull FileReferenceSetParameters parameters,
                                       @NotNull VirtualFile hostFile,
                                       @NotNull Processor<? super FileTargetContext> processor) {
    PsiElement element = parameters.getElement();
    getTargetContexts(element.getProject(), hostFile, parameters.isAbsolutePathReference()).forEach(processor::process);
    return true;
  }


  /**
   * Provides file target contexts, locations where users can create a file, depending on passed {@code file}.
   *
   * @apiNote before running this method {@link #isMine(Project, VirtualFile)} should be called.
   */
  @NotNull
  public Collection<FileTargetContext> getTargetContexts(@NotNull Project project,
                                                         @NotNull VirtualFile hostFile,
                                                         boolean isAbsoluteReference) {
    if (isAbsoluteReference) {
      ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      Module module = index.getModuleForFile(hostFile);
      if (module == null) {
        return emptyList();
      }
      return ContainerUtil.map(getRoots(module, hostFile), FileTargetContext::new);
    }

    return ContainerUtil.map(getContexts(project, hostFile), FileTargetContext::new);
  }

  @NotNull
  public String trimUrl(@NotNull String url) {
    return url;
  }

  public @NotNull List<? extends @NotNull LocalQuickFix> registerFixes(@NotNull FileReference reference) {
    return emptyList();
  }

  @Nullable
  public PsiFileSystemItem getPsiFileSystemItem(@NotNull Project project, @NotNull final VirtualFile file) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    return getPsiFileSystemItem(psiManager, file);
  }

  @Nullable
  public static PsiFileSystemItem getPsiFileSystemItem(@NotNull PsiManager psiManager, @NotNull VirtualFile file) {
    return file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
  }

  /**
   * @deprecated use {@link #getRoots(Module, VirtualFile)} that provides better context
   */
  @Deprecated
  @NotNull
  public Collection<PsiFileSystemItem> getRoots(@NotNull Module module) {
    return emptyList();
  }

  /**
   * @return true if the helper is an instance of {@link NullFileReferenceHelper}
   */
  public boolean isFallback() {
    return false;
  }
}
