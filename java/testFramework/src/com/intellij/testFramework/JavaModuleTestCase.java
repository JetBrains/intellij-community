// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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

  protected @NotNull Module createModule(@NotNull Path moduleFile) {
    return createModule(moduleFile, JavaModuleType.getModuleType());
  }

  protected @NotNull Module createModule(@NotNull Path moduleFile, @NotNull ModuleType<?> moduleType) {
    Module module = WriteAction.compute(() -> ModuleManager.getInstance(myProject).newModule(moduleFile, moduleType.getId()));
    myModulesToDispose.add(module);
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    return module;
  }

  protected @NotNull Module createModule(@NotNull String path, @NotNull ModuleType<?> moduleType) {
    Module module = WriteAction.compute(() -> ModuleManager.getInstance(myProject).newModule(path, moduleType.getId()));
    myModulesToDispose.add(module);
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    return module;
  }

  protected @NotNull Module loadModule(@NotNull VirtualFile file) {
    return loadModule(file.toNioPath());
  }

  protected final @NotNull Module loadModule(@NotNull Path modulePath) {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module module;
    try {
      module = ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Module, Exception>)() -> {
        return moduleManager.loadModule(modulePath);
      });
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    myModulesToDispose.add(module);
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    return module;
  }

  protected @Nullable Module loadAllModulesUnder(@NotNull VirtualFile rootDir) {
    return loadAllModulesUnder(rootDir, null);
  }

  protected @Nullable Module loadAllModulesUnder(@NotNull VirtualFile rootDir, final @Nullable Consumer<? super Module> moduleConsumer) {
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

    Module module = result.get();
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    return module;
  }

  protected final @NotNull Module createModuleFromTestData(@NotNull String dirInTestData,
                                                           @NotNull String newModuleFileName,
                                                           @NotNull ModuleType<?> moduleType,
                                                           boolean addSourceRoot)
    throws IOException {
    VirtualFile moduleDir = getTempDir().createVirtualDir();
    FileUtil.copyDir(new File(dirInTestData), moduleDir.toNioPath().toFile());
    moduleDir.refresh(false, true);
    Module module = createModule(moduleDir.toNioPath().resolve(newModuleFileName), moduleType);
    if (addSourceRoot) {
      PsiTestUtil.addSourceContentToRoots(module, moduleDir);
    }
    else {
      PsiTestUtil.addContentRoot(module, moduleDir);
    }
    return module;
  }
}
