// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class EntityIndexingServiceTest extends EntityIndexingServiceTestBase {

  public void testIndexingModule() throws Exception {
    if (!Registry.is("use.workspace.file.index.for.partial.scanning")) {
      doTest(this::createModuleAndSourceRoot, this::removeModule,
             pair -> IndexableEntityProviderMethods.INSTANCE.createModuleContentIterators(pair.getFirst()));
    }
  }

  @NotNull
  private Pair<Module, VirtualFile> createModuleAndSourceRoot() throws IOException {
    File root = createTempDir("otherModule");
    Module module = createModuleAt("otherModule", getProject(), getModuleType(), root.toPath());
    VirtualFile moduleDir = getOrCreateModuleDir(module);
    VirtualFile src = moduleDir.createChildDirectory(this, "src");
    PsiTestUtil.addSourceRoot(module, src);
    return new Pair<>(module, src);
  }

  private void removeModule(Pair<Module, VirtualFile> data) throws IOException {
    ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
    modifiableModel.disposeModule(data.getFirst());
    modifiableModel.commit();
    data.getSecond().getParent().delete(this);
  }

}
