// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FilePathReferenceProvider extends PsiReferenceProvider {

  private final boolean myEndingSlashNotAllowed;

  public FilePathReferenceProvider() {
    this(true);
  }

  public FilePathReferenceProvider(boolean endingSlashNotAllowed) {
    myEndingSlashNotAllowed = endingSlashNotAllowed;
  }

  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull String text, int offset, final boolean soft) {
    return getReferencesByElement(element, text, offset, soft, Module.EMPTY_ARRAY);
  }

  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                         @NotNull String text,
                                                         int offset,
                                                         final boolean soft,
                                                         final Module @NotNull ... forModules) {
    return new FileReferenceSet(text, element, offset, this, true, myEndingSlashNotAllowed) {


      @Override
      protected boolean isSoft() {
        return soft;
      }

      @Override
      public boolean isAbsolutePathReference() {
        return true;
      }

      @Override
      public boolean couldBeConvertedTo(boolean relative) {
        return !relative;
      }

      @Override
      public boolean absoluteUrlNeedsStartSlash() {
        final String s = getPathString();
        return s != null && !s.isEmpty() && s.charAt(0) == '/';
      }

      @Override
      @NotNull
      public Collection<PsiFileSystemItem> computeDefaultContexts() {
        if (forModules.length > 0) {
          Set<PsiFileSystemItem> rootsForModules = new LinkedHashSet<>();
          for (Module forModule : forModules) {
            rootsForModules.addAll(getRoots(forModule, true));
          }
          return rootsForModules;
        }

        return getRoots(ModuleUtilCore.findModuleForPsiElement(getElement()), true);
      }

      @Override
      public FileReference createFileReference(final TextRange range, final int index, final String text) {
        return FilePathReferenceProvider.this.createFileReference(this, range, index, text);
      }

      @Override
      protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
        return element1 -> isPsiElementAccepted(element1);
      }
    }.getAllReferences();
  }

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return target instanceof PsiFileSystemItem;
  }

  protected boolean isPsiElementAccepted(PsiElement element) {
    return !(element instanceof PsiJavaFile && element instanceof PsiCompiledElement);
  }

  protected FileReference createFileReference(FileReferenceSet referenceSet, final TextRange range, final int index, final String text) {
    return new FileReference(referenceSet, range, index, text);
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    String text = null;
    if (element instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)element).getValue();
      if (value instanceof String) {
        text = (String)value;
      }
    }
    //else if (element instanceof XmlAttributeValue) {
    //  text = ((XmlAttributeValue)element).getValue();
    //}
    if (text == null) return PsiReference.EMPTY_ARRAY;
    return getReferencesByElement(element, text, 1, true);
  }

  @NotNull
  public static Collection<PsiFileSystemItem> getRoots(@Nullable final Module thisModule, boolean includingClasses) {
    if (thisModule == null) return Collections.emptyList();

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(thisModule);
    Set<PsiFileSystemItem> result = new LinkedHashSet<>();
    final PsiManager psiManager = PsiManager.getInstance(thisModule.getProject());
    if (includingClasses) {
      VirtualFile[] libraryUrls = moduleRootManager.orderEntries().getAllLibrariesAndSdkClassesRoots();
      for (VirtualFile file : libraryUrls) {
        PsiDirectory directory = psiManager.findDirectory(file);
        if (directory != null) {
          result.add(directory);
        }
      }
    }

    VirtualFile[] sourceRoots = moduleRootManager.orderEntries().recursively()
      .withoutSdk().withoutLibraries()
      .sources().usingCache().getRoots();
    for (VirtualFile root : sourceRoots) {
      final PsiDirectory directory = psiManager.findDirectory(root);
      if (directory != null) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (aPackage != null && aPackage.getName() != null) {
          // package prefix
          result.add(PackagePrefixFileSystemItemImpl.create(directory));
        }
        else {
          result.add(directory);
        }
      }
    }
    return result;
  }
}
