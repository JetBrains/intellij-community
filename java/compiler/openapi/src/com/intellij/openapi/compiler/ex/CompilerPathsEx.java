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
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
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
   * A decorator for a .class file (library classes usually live inside .jars and are better accessed via VFS; compiled classes
   * may be absent from VFS and are better accessed via I/O files).
   */
  public interface ClassFileDescriptor {
    /** Returns file contents. */
    byte[] loadFileBytes() throws IOException;

    /** Returns file path in a system-independent format. */
    String getPath();
  }

  private static class VirtualClassFileDescriptor implements ClassFileDescriptor {
    private final VirtualFile myClassFile;

    private VirtualClassFileDescriptor(VirtualFile file) {
      myClassFile = file;
    }

    @Override
    public byte[] loadFileBytes() throws IOException {
      return myClassFile.contentsToByteArray(false);
    }

    @Override
    public String getPath() {
      return myClassFile.getPath();
    }
  }

  private static class IOClassFileDescriptor implements ClassFileDescriptor {
    private final File myClassFile;

    private IOClassFileDescriptor(File classFile) {
      myClassFile = classFile;
    }

    @Override
    public byte[] loadFileBytes() throws IOException {
      return FileUtil.loadFileBytes(myClassFile);
    }

    @Override
    public String getPath() {
      return myClassFile.getPath();
    }
  }

  @Nullable
  public static ClassFileDescriptor findClassFileInOutput(@NotNull PsiClass aClass) {
    String jvmClassName = getJVMClassName(aClass);
    if (jvmClassName != null) {
      ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(aClass.getProject());

      PsiElement originalClass = aClass.getOriginalElement();
      if (originalClass instanceof PsiCompiledElement) {
        // compiled class; looking for a right .class file
        VirtualFile file = originalClass.getContainingFile().getVirtualFile();
        if (file != null) {
          String classFileName = StringUtil.getShortName(jvmClassName) + ".class";
          if (index.isInLibraryClasses(file)) {
            VirtualFile classFile = file.getParent().findChild(classFileName);
            if (classFile != null) {
              return new VirtualClassFileDescriptor(classFile);
            }
          }
          else {
            File classFile = new File(file.getParent().getPath(), classFileName);
            if (classFile.isFile()) {
              return new IOClassFileDescriptor(classFile);
            }
          }
        }
      }
      else {
        // source code; looking for a .class file in compiler output
        VirtualFile file = aClass.getContainingFile().getVirtualFile();
        if (file != null) {
          Module module = index.getModuleForFile(file);
          if (module != null) {
            CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
            if (extension != null) {
              boolean inTests = index.isInTestSourceContent(file);
              VirtualFile classRoot = inTests ? extension.getCompilerOutputPathForTests() : extension.getCompilerOutputPath();
              if (classRoot != null) {
                String relativePath = jvmClassName.replace('.', '/') + ".class";
                File classFile = new File(classRoot.getPath(), relativePath);
                if (classFile.exists()) {
                  return new IOClassFileDescriptor(classFile);
                }
              }
            }
          }
        }
      }
    }

    return null;
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