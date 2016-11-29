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

package com.intellij.openapi.paths;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 *
 * @see PathReferenceProvider
 */
public abstract class PathReferenceManager {
  public static final ExtensionPointName<PathReferenceProvider> PATH_REFERENCE_PROVIDER_EP = ExtensionPointName.create("com.intellij.pathReferenceProvider");
  public static final ExtensionPointName<PathReferenceProvider> ANCHOR_REFERENCE_PROVIDER_EP = ExtensionPointName.create("com.intellij.anchorReferenceProvider");

  @NotNull
  public static PathReferenceManager getInstance(){
    return ServiceManager.getService(PathReferenceManager.class);
  }

  /**
   * Create web path references for given PsiElement.
   * The same as {@link #createReferences(com.intellij.psi.PsiElement, boolean, boolean, boolean, PathReferenceProvider[])} with
   * endingSlashNotAllowed = true and relativePathsAllowed = true.
   *
   * @param psiElement the underlying PSI element.
   * @param soft set this to true to create soft references (see {@link com.intellij.psi.PsiReference#isSoft()}).
   * @param additionalProviders additional providers to process.
   * @return created references or an empty array.
   */
  @NotNull
  public abstract PsiReference[] createReferences(@NotNull PsiElement psiElement,
                                                  boolean soft,
                                                  PathReferenceProvider... additionalProviders);

  /**
   * Create web path references for given PsiElement.
   *
   * @param psiElement the underlying PSI element.
   * @param soft set this to true to create soft references (see {@link com.intellij.psi.PsiReference#isSoft()}).
   * @param endingSlashNotAllowed true if paths like "/foo/" should not be resolved.
   * @param relativePathsAllowed true if the folder of the file containing the PsiElement should be used as "root".
   *        Otherwise, web application root will be used.
   *@param additionalProviders additional providers to process.  @return created references or an empty array.
   */
  @NotNull
  public abstract PsiReference[] createReferences(@NotNull PsiElement psiElement,
                                                  boolean soft,
                                                  boolean endingSlashNotAllowed,
                                                  boolean relativePathsAllowed, PathReferenceProvider... additionalProviders);

  public abstract PsiReference[] createReferences(@NotNull PsiElement psiElement,
                                                  boolean soft,
                                                  boolean endingSlashNotAllowed,
                                                  boolean relativePathsAllowed, FileType[] suitableFileTypes, PathReferenceProvider... additionalProviders);

  @NotNull
  public abstract PsiReference[] createCustomReferences(@NotNull PsiElement psiElement, 
                                                        boolean soft,
                                                        PathReferenceProvider... providers);


  @Nullable
  public abstract PathReference getPathReference(@NotNull String path,
                                                 @NotNull PsiElement element,
                                                 PathReferenceProvider... additionalProviders);

  @Nullable
  public abstract PathReference getCustomPathReference(@NotNull String path, @NotNull Module module, @NotNull PsiElement element, PathReferenceProvider... providers);

  @NotNull
  public abstract PathReferenceProvider getGlobalWebPathReferenceProvider();

  @NotNull
  public abstract PathReferenceProvider createStaticPathReferenceProvider(final boolean relativePathsAllowed);

  public static PsiReference[] getReferencesFromProvider(@NotNull PathReferenceProvider provider, @NotNull PsiElement psiElement, boolean soft) {
    final ArrayList<PsiReference> references = new ArrayList<>();
    provider.createReferences(psiElement, references, soft);
    return references.toArray(new PsiReference[references.size()]);    
  }
}
