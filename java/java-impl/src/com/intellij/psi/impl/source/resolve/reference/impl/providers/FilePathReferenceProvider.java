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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.*;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author cdr
 */
public class FilePathReferenceProvider extends PsiReferenceProvider {

  private final boolean myEndingSlashNotAllowed;

  public FilePathReferenceProvider() {
    this(true);
  }

  public FilePathReferenceProvider(boolean endingSlashNotAllowed) {
    myEndingSlashNotAllowed = endingSlashNotAllowed;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, String text, int offset, final boolean soft) {
    return getReferencesByElement(element, text, offset, soft, Module.EMPTY_ARRAY);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element,
                                               String text,
                                               int offset,
                                               final boolean soft,
                                               final @NotNull Module... forModules) {
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
        return s != null && s.length() > 0 && s.charAt(0) == '/';
      }

      @Override
      @NotNull
      public Collection<PsiFileSystemItem> computeDefaultContexts() {
        Set<PsiFileSystemItem> systemItems = new HashSet<PsiFileSystemItem>();
        if (forModules.length > 0) {
          for (Module forModule : forModules) {
            systemItems.addAll(getRoots(forModule, true));
          }
        } else {
          systemItems.addAll(getRoots(ModuleUtil.findModuleForPsiElement(getElement()), true));
        }
        return systemItems;
      }

      @Override
      public FileReference createFileReference(final TextRange range, final int index, final String text) {
        return FilePathReferenceProvider.this.createFileReference(this, range, index, text);
      }

      @Override
      protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
        return new Condition<PsiFileSystemItem>() {
          @Override
          public boolean value(final PsiFileSystemItem element) {
            return isPsiElementAccepted(element);
          }
        };
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
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
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
  public static Collection<PsiFileSystemItem> getRoots(final Module thisModule, boolean includingClasses) {
    if (thisModule == null) return Collections.emptyList();
    Set<Module> modules = new com.intellij.util.containers.HashSet<Module>();
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(thisModule);
    ModuleUtil.getDependencies(thisModule, modules);
    List<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();
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

    VirtualFile[] sourceRoots = moduleRootManager.orderEntries().recursively().withoutSdk().withoutLibraries().getSourceRoots();
    for (VirtualFile root : sourceRoots) {
      final PsiDirectory directory = psiManager.findDirectory(root);
      if (directory != null) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (aPackage != null && aPackage.getName() != null) {
          // package prefix
          result.add(PackagePrefixFileSystemItem.create(directory));
        }
        else {
          result.add(directory);
        }
      }
    }
    return result;
  }
}
