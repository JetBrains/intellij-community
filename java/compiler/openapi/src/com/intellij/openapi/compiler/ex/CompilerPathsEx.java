/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.compiler.ex;

import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class CompilerPathsEx extends CompilerPaths {
  @NotNull
  public static String[] getOutputPaths(@NotNull Module[] modules) {
    Set<String> outputPaths = new OrderedSet<>();
    for (Module module : modules) {
      CompilerModuleExtension compilerModuleExtension = !module.isDisposed()? CompilerModuleExtension.getInstance(module) : null;
      if (compilerModuleExtension == null) continue;

      String outputPathUrl = compilerModuleExtension.getCompilerOutputUrl();
      if (outputPathUrl != null) {
        outputPaths.add(VirtualFileManager.extractPath(outputPathUrl).replace('/', File.separatorChar));
      }

      String outputPathForTestsUrl = compilerModuleExtension.getCompilerOutputUrlForTests();
      if (outputPathForTestsUrl != null) {
        outputPaths.add(VirtualFileManager.extractPath(outputPathForTestsUrl).replace('/', File.separatorChar));
      }

      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      for (OrderEnumerationHandler.Factory handlerFactory : OrderEnumerationHandler.EP_NAME.getExtensions()) {
        if (handlerFactory.isApplicable(module)) {
          OrderEnumerationHandler handler = handlerFactory.createHandler(module);
          List<String> outputUrls = new SmartList<>();
          handler.addCustomModuleRoots(OrderRootType.CLASSES, moduleRootManager, outputUrls, true, true);
          for (String outputUrl : outputUrls) {
            outputPaths.add(VirtualFileManager.extractPath(outputUrl).replace('/', File.separatorChar));
          }
        }
      }
    }
    return ArrayUtil.toStringArray(outputPaths);
  }

  /**
   * Presents .class file in form of {@link File} for files inside output directories or {@link VirtualFile} inside jars for library classes.
   * Building virtual files for output directories is not feasible for the task and io.File won't work inside jars.
   */
  public interface ClassFileDescriptor {
    /**
     * Loads content of the class file
     */
    byte[] loadFileBytes() throws IOException;

    /**
     * Returns system independent path to the class file
     */
    String getPath();
  }

  @Nullable
  public static ClassFileDescriptor findClassFileInOutput(@NotNull PsiClass sourceClass) {
    String classVMName = getJVMClassName(sourceClass);
    if (classVMName == null) {
      return null;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(sourceClass);
    if (module == null){
      final Project project = sourceClass.getProject();
      final PsiClass topLevelClass = PsiUtil.getTopLevelClass(sourceClass);
      final String qualifiedName = topLevelClass != null ? topLevelClass.getQualifiedName() : null;
      final PsiClass aClass = qualifiedName != null
                              ? JavaPsiFacade.getInstance(project).findClass(qualifiedName, sourceClass.getResolveScope())
                              : null;
      if (aClass != null) {
        final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(aClass);
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        if (virtualFile != null && fileIndex.isInLibraryClasses(virtualFile)) {
          return new ClassFileDescriptor() {
            @Override
            public byte[] loadFileBytes() throws IOException {
              return virtualFile.contentsToByteArray();
            }

            @Override
            public String getPath() {
              return virtualFile.getPath();
            }
          };
        }
      }
      return null;
    }

    final PsiFile containingFile = sourceClass.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;
    final CompilerModuleExtension moduleExtension = CompilerModuleExtension.getInstance(module);
    if (moduleExtension == null) return null;
    VirtualFile classRoot;
    if (ProjectRootManager.getInstance(module.getProject()).getFileIndex().isInTestSourceContent(virtualFile)) {
      classRoot = moduleExtension.getCompilerOutputPathForTests();
    }
    else {
      classRoot = moduleExtension.getCompilerOutputPath();
    }
    if (classRoot == null) return null;

    String classFilePath = classRoot.getPath() + "/" + classVMName.replace('.', '/') + ".class";

    final File classFile = new File(classFilePath);
    if (!classFile.exists()) {
      return null;
    }
    return new ClassFileDescriptor() {
      @Override
      public byte[] loadFileBytes() throws IOException {
        return FileUtil.loadFileBytes(classFile);
      }

      @Override
      public String getPath() {
        return FileUtil.toSystemIndependentName(classFile.getPath());
      }
    };
  }

  @Nullable
  private static String getJVMClassName(PsiClass aClass) {
    if (!(aClass instanceof PsiAnonymousClass)) {
      return ClassUtil.getJVMClassName(aClass);
    }

    PsiClass containingClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    if (containingClass != null) {
      return getJVMClassName(containingClass) + JavaAnonymousClassesHelper.getName((PsiAnonymousClass)aClass);
    }

    return null;
  }
}