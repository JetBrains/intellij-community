// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.SdkTestCase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.entities.LibraryId;
import com.intellij.platform.workspace.jps.entities.LibraryTableId;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods;
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class EntityIndexingServiceTest extends EntityIndexingServiceTestBase {

  public void testIndexingModule() throws Exception {
    doTest(this::createModuleAndSourceRoot, this::removeModule,
           pair -> IndexableEntityProviderMethods.INSTANCE.createModuleContentIterators(pair.getFirst()));
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

  public void testIndexingProjectLibrary() throws Exception {
    if (!Registry.is("use.workspace.file.index.for.partial.scanning", false)) {
      doTest(this::createProjectLibrary, this::removeProjectLibrary, LibraryIndexableFilesIteratorImpl::createIteratorList);
    }
  }

  @NotNull
  private LibraryBridge createProjectLibrary() {
    return (LibraryBridge)createLibrary(LibraryTablesRegistrar.getInstance().getLibraryTable(getProject()));
  }

  @NotNull
  private Library createLibrary(LibraryTable libraryTable) {
    LibraryTable.ModifiableModel libraryTableModifiableModel = libraryTable.getModifiableModel();
    Library lib = libraryTableModifiableModel.createLibrary("lib");
    libraryTableModifiableModel.commit();

    OrderEntryUtil.addLibraryToRoots(getModule(), lib, DependencyScope.RUNTIME, false);
    return lib;
  }

  private void removeProjectLibrary(LibraryBridge library) {
    removeLibrary(library, LibraryTablesRegistrar.getInstance().getLibraryTable(getProject()));
  }

  private void removeLibrary(Library library, LibraryTable libraryTable) {
    ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    LibraryOrderEntry libraryOrderEntry = OrderEntryUtil.findLibraryOrderEntry(rootModel, library);
    rootModel.removeOrderEntry(libraryOrderEntry);
    rootModel.commit();

    LibraryTable.ModifiableModel libraryTableModifiableModel = libraryTable.getModifiableModel();
    libraryTableModifiableModel.removeLibrary(library);
    libraryTableModifiableModel.commit();
  }

  public void testIndexingGlobalLibrary() throws Exception {
    if (!Registry.is("use.workspace.file.index.for.partial.scanning", false)) {
      doTest(this::createGlobalLibrary, this::removeGlobalLibrary,
             pair -> LibraryIndexableFilesIteratorImpl.createIteratorList(pair.getFirst()));
    }
  }

  @NotNull
  private Pair<Library, LibraryId> createGlobalLibrary() {
    Library library = createLibrary(LibraryTablesRegistrar.getInstance().getLibraryTable());
    return new Pair<>(library,
                      new LibraryId(library.getName(), new LibraryTableId.GlobalLibraryTableId(library.getTable().getTableLevel())));
  }

  private void removeGlobalLibrary(Pair<Library, LibraryId> libraryPair) {
    removeLibrary(libraryPair.getFirst(), LibraryTablesRegistrar.getInstance().getLibraryTable());
  }

  public void testIndexingModuleLibrary() throws Exception {
    if (!Registry.is("use.workspace.file.index.for.partial.scanning", false)) {
      doTest(this::createModuleLibrary, this::removeModuleLibrary, LibraryIndexableFilesIteratorImpl::createIteratorList);
    }
  }

  private void removeModuleLibrary(Library library) {
    ModuleRootManagerEx moduleRootManager = ModuleRootManagerEx.getInstanceEx(getModule());
    ModifiableRootModel model = moduleRootManager.getModifiableModel();
    LibraryTable table = model.getModuleLibraryTable();
    table.removeLibrary(library);
    model.commit();
  }

  @NotNull
  private LibraryBridge createModuleLibrary() {
    ModuleRootManagerEx moduleRootManager = ModuleRootManagerEx.getInstanceEx(getModule());
    ModifiableRootModel model = moduleRootManager.getModifiableModel();
    LibraryTable table = model.getModuleLibraryTable();
    Library lib = table.createLibrary("lib");
    model.commit();
    return (LibraryBridge)lib;
  }

  public void testIndexingSdk() throws Exception {
    doTest(this::createSdk, this::removeSdk, sdk -> IndexableEntityProviderMethods.INSTANCE.createIterators(sdk));
  }

  @NotNull
  private Sdk createSdk() {
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    Sdk result = jdkTable.createSdk("SDK", SdkTestCase.DependentTestSdkType.INSTANCE);
    jdkTable.addJdk(result);
    ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    model.setSdk(result);
    model.commit();
    return result;
  }

  private void removeSdk(Sdk sdk) {
    ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    model.setSdk(null);
    model.commit();
    ProjectJdkTable.getInstance().removeJdk(sdk);
  }
}
