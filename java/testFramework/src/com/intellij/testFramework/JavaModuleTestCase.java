// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public abstract class JavaModuleTestCase extends JavaProjectTestCase {
  protected final Collection<Module> myModulesToDispose = new ArrayList<>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModulesToDispose.clear();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (!myModulesToDispose.isEmpty()) {
        WriteAction.run(() -> {
          ModuleManager moduleManager = ModuleManager.getInstance(myProject);
          for (Module module : myModulesToDispose) {
            try {
              String moduleName = module.getName();
              if (moduleManager.findModuleByName(moduleName) != null) {
                moduleManager.disposeModule(module);
              }
            }
            catch (Throwable e) {
              addSuppressedException(e);
            }
          }
        });
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myModulesToDispose.clear();
      super.tearDown();
    }
  }

  @NotNull
  protected Module createModule(@NotNull File moduleFile) {
    return createModule(moduleFile, StdModuleTypes.JAVA);
  }

  @NotNull
  protected Module createModule(@NotNull final File moduleFile, @NotNull ModuleType moduleType) {
    final String path = moduleFile.getAbsolutePath();
    return createModule(path, moduleType);
  }

  @NotNull
  protected Module createModule(@NotNull String path, @NotNull ModuleType moduleType) {
    Module module = WriteAction.compute(() -> ModuleManager.getInstance(myProject).newModule(path, moduleType.getId()));

    myModulesToDispose.add(module);
    return module;
  }

  @NotNull
  protected Module loadModule(@NotNull VirtualFile file) {
    return loadModule(file.getPath());
  }

  @NotNull
  protected Module loadModule(@NotNull String modulePath) {
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module module;
    try {
      module = ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Module, Exception>)() -> moduleManager.loadModule(
        FileUtil.toSystemIndependentName(modulePath)));
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    myModulesToDispose.add(module);
    return module;
  }

  @Nullable
  protected Module loadAllModulesUnder(@NotNull VirtualFile rootDir) {
    return loadAllModulesUnder(rootDir, null);
  }

  @Nullable
  protected Module loadAllModulesUnder(@NotNull VirtualFile rootDir, @Nullable final Consumer<? super Module> moduleConsumer) {
    final Ref<Module> result = Ref.create();

    VfsUtilCore.visitChildrenRecursively(rootDir, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() && file.getName().endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)) {
          Module module = loadModule(file);
          if (moduleConsumer != null) {
            moduleConsumer.consume(module);
          }
          result.setIfNull(module);
        }
        return true;
      }
    });

    return result.get();
  }

  @NotNull
  protected Module createModuleFromTestData(@NotNull String dirInTestData, @NotNull String newModuleFileName, @NotNull ModuleType moduleType,
                                            boolean addSourceRoot)
    throws IOException {
    final File dirInTestDataFile = new File(dirInTestData);
    assertTrue(dirInTestDataFile.isDirectory());
    final File moduleDir = createTempDirectory();
    FileUtil.copyDir(dirInTestDataFile, moduleDir);
    final Module module = createModule(moduleDir + "/" + newModuleFileName, moduleType);
    final VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleDir);
    assertNotNull(root);
    WriteCommandAction.writeCommandAction(module.getProject()).run(() -> root.refresh(false, true));
    if (addSourceRoot) {
      PsiTestUtil.addSourceContentToRoots(module, root);
    }
    else {
      PsiTestUtil.addContentRoot(module, root);
    }
    return module;
  }
}
