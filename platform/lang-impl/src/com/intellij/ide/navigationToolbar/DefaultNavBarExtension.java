/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.InternalModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiFileSystemItemProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 * @since 04-Feb-2008
 */
public class DefaultNavBarExtension extends AbstractNavBarModelExtension {
  @Override
  @Nullable
  public String getPresentableText(final Object object) {
    if (object instanceof Project) {
      return ((Project)object).getName();
    }
    else if (object instanceof Module) {
      return ((Module)object).getName();
    }
    else if (object instanceof PsiFile) {
      VirtualFile file = ((PsiFile)object).getVirtualFile();
      return file != null ? file.getPresentableName() : ((PsiFile)object).getName();
    }
    else if (object instanceof PsiDirectory) {
      return ((PsiDirectory)object).getVirtualFile().getName();
    }
    else if (object instanceof JdkOrderEntry) {
      return ((JdkOrderEntry)object).getJdkName();
    }
    else if (object instanceof LibraryOrderEntry) {
      final String libraryName = ((LibraryOrderEntry)object).getLibraryName();
      return libraryName != null ? libraryName : AnalysisScopeBundle.message("package.dependencies.library.node.text");
    }
    else if (object instanceof ModuleOrderEntry) {
      final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)object;
      return moduleOrderEntry.getModuleName();
    }
    return null;
  }

  @Override
  public PsiElement adjustElement(final PsiElement psiElement) {
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null) return containingFile;
    return psiElement;
  }

  @Override
  public boolean processChildren(final Object object, final Object rootElement, final Processor<Object> processor) {
    if (object instanceof Project) {
      return processChildren((Project)object, processor);
    }
    else if (object instanceof Module) {
      return processChildren((Module)object, processor);
    }
    else if (object instanceof PsiDirectoryContainer) {
      final PsiDirectoryContainer psiPackage = (PsiDirectoryContainer)object;
      final PsiDirectory[] psiDirectories = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiDirectory[]>() {
          @Override
          public PsiDirectory[] compute() {
            return rootElement instanceof Module
                   ? psiPackage.getDirectories(GlobalSearchScope.moduleScope((Module)rootElement))
                   : psiPackage.getDirectories();
          }
        }
      );
      for (PsiDirectory psiDirectory : psiDirectories) {
        if (!processChildren(psiDirectory, rootElement, processor)) return false;
      }
      return true;
    }
    else if (object instanceof PsiDirectory) {
      return processChildren((PsiDirectory)object, rootElement, processor);
    }
    else if (object instanceof PsiFileSystemItem) {
      return processChildren((PsiFileSystemItem)object, processor);
    }
    return true;
  }

  private static boolean processChildren(final Project object, final Processor<Object> processor) {
    return ApplicationManager.getApplication().runReadAction(
      new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          for (Module module : ModuleManager.getInstance(object).getModules()) {
            if (!(ModuleType.get(module) instanceof InternalModuleType) && !processor.process(module)) return false;
          }
          return true;
        }
      }
    );
  }

  private static boolean processChildren(Module module, Processor<Object> processor) {
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    VirtualFile[] roots = moduleRootManager.getContentRoots();
    for (final VirtualFile root : roots) {
      final PsiDirectory psiDirectory = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiDirectory>() {
          @Override
          public PsiDirectory compute() {
            return psiManager.findDirectory(root);
          }
        }
      );
      if (psiDirectory != null) {
        if (!processor.process(psiDirectory)) return false;
      }
    }
    return true;
  }

  private static boolean processChildren(final PsiDirectory object, final Object rootElement, final Processor<Object> processor) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final ModuleFileIndex moduleFileIndex =
          rootElement instanceof Module ? ModuleRootManager.getInstance((Module)rootElement).getFileIndex() : null;
        final PsiElement[] children = object.getChildren();
        for (PsiElement child : children) {
          if (child != null && child.isValid()) {
            if (moduleFileIndex != null) {
              final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(child);
              if (virtualFile != null && !moduleFileIndex.isInContent(virtualFile)) continue;
            }
            if (!processor.process(child)) return false;
          }
        }
        return true;
      }
    });
  }

  private static boolean processChildren(final PsiFileSystemItem object, final Processor<Object> processor) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return object.processChildren(new PsiFileSystemItemProcessor() {
          @Override
          public boolean acceptItem(String name, boolean isDirectory) {
            return true;
          }

          @Override
          public boolean execute(@NotNull PsiFileSystemItem element) {
            return processor.process(element);
          }
        });
      }
    });
  }

  @Nullable
  @Override
  public PsiElement getParent(PsiElement psiElement) {
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null) {
      PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      if (containingDirectory != null) {
        return containingDirectory;
      }
    }
    else if (psiElement instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)psiElement;
      Project project = psiElement.getProject();

      PsiDirectory parentDirectory = psiDirectory.getParentDirectory();

      if (parentDirectory == null) {
        VirtualFile jar = PathUtil.getLocalFile(psiDirectory.getVirtualFile());
        if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(jar)) {
          parentDirectory = PsiManager.getInstance(project).findDirectory(jar.getParent());
        }
      }
      return parentDirectory;
    }
    else if (psiElement instanceof PsiFileSystemItem) {
      VirtualFile virtualFile = ((PsiFileSystemItem)psiElement).getVirtualFile();
      if (virtualFile == null) return null;
      PsiManager psiManager = psiElement.getManager();
      PsiElement resultElement;
      if (virtualFile.isDirectory()) {
        resultElement = psiManager.findDirectory(virtualFile);
      }
      else {
        resultElement = psiManager.findFile(virtualFile);
      }
      if (resultElement == null) return null;
      VirtualFile parentVFile = virtualFile.getParent();
      if (parentVFile != null) {
        PsiDirectory parentDirectory = psiManager.findDirectory(parentVFile);
        if (parentDirectory != null) {
          return parentDirectory;
        }
      }
    }
    return null;
  }
}
