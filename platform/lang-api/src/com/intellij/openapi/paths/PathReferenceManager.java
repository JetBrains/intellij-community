// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.paths;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
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
    return ApplicationManager.getApplication().getService(PathReferenceManager.class);
  }

  /**
   * Create web path references for given PsiElement.
   * The same as {@link #createReferences(PsiElement, boolean, boolean, boolean, PathReferenceProvider[])} with
   * endingSlashNotAllowed = true and relativePathsAllowed = true.
   *
   * @param psiElement the underlying PSI element.
   * @param soft set this to true to create soft references (see {@link PsiReference#isSoft()}).
   * @param additionalProviders additional providers to process.
   * @return created references or an empty array.
   */
  public abstract PsiReference @NotNull [] createReferences(@NotNull PsiElement psiElement,
                                                            boolean soft,
                                                            PathReferenceProvider... additionalProviders);

  /**
   * Create web path references for given PsiElement.
   *
   * @param psiElement the underlying PSI element.
   * @param soft set this to true to create soft references (see {@link PsiReference#isSoft()}).
   * @param endingSlashNotAllowed true if paths like "/foo/" should not be resolved.
   * @param relativePathsAllowed true if the folder of the file containing the PsiElement should be used as "root".
   *        Otherwise, web application root will be used.
   *@param additionalProviders additional providers to process.  @return created references or an empty array.
   */
  public abstract PsiReference @NotNull [] createReferences(@NotNull PsiElement psiElement,
                                                            boolean soft,
                                                            boolean endingSlashNotAllowed,
                                                            boolean relativePathsAllowed, PathReferenceProvider... additionalProviders);

  public abstract PsiReference[] createReferences(@NotNull PsiElement psiElement,
                                                  boolean soft,
                                                  boolean endingSlashNotAllowed,
                                                  boolean relativePathsAllowed, FileType[] suitableFileTypes, PathReferenceProvider... additionalProviders);

  public abstract PsiReference @NotNull [] createCustomReferences(@NotNull PsiElement psiElement,
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
    return references.toArray(PsiReference.EMPTY_ARRAY);
  }
}
