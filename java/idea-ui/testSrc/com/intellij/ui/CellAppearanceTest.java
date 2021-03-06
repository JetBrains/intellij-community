// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.roots.ui.util.SimpleTextCellAppearance;
import com.intellij.openapi.roots.ui.util.ValidFileCellAppearance;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Iterator;

public class CellAppearanceTest extends JavaModuleTestCase {
  private OrderEntryAppearanceService myService;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myService = OrderEntryAppearanceService.getInstance();
  }

  @Override
  public void tearDown() throws Exception {
    myService = null;
    super.tearDown();
  }

  public void testGeneralOrderEntry() {
    MockOrderEntry orderEntry = new MockOrderEntry("name", true);
    CompositeAppearance appearance = (CompositeAppearance)simpleTextAppearance(orderEntry);
    assertEquals("name", appearance.getText());
    checkSingleSection(appearance, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public void testInvalidOrderEntry() {
    MockOrderEntry orderEntry = new MockOrderEntry("name", false);
    CellAppearanceEx appearance = simpleTextAppearance(orderEntry);
    assertEquals("name", appearance.getText());
    checkSingleSection(appearance, SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  public void testLibraryOrderEntry() {
    ModifiableRootModel rootModel = createRootModel();
    Library library = rootModel.getModuleLibraryTable().createLibrary();
    VirtualFile vFile = addClassRoot(library);
    ValidFileCellAppearance appearance = (ValidFileCellAppearance)myService.forOrderEntry(myProject, getLastOrderEntry(rootModel), false);
    assertEquals(vFile, appearance.getFile());
  }

  public void testSourceOrderEntry() {
    ModifiableRootModel rootModel = createRootModel();
    OrderEntry[] order = rootModel.getOrderEntries();
    CellAppearanceEx appearance = simpleTextAppearance(order[0]);
    assertEquals(order[0].getPresentableName(), appearance.getText());
    checkSingleSection(appearance, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
  }

  private @NotNull Module createTempModule() {
    return createModule(getTempDir().newPath("module.iml"));
  }

  private static void checkSingleSection(CellAppearanceEx appearance, SimpleTextAttributes expectedAttribute) {
    if (appearance instanceof SimpleTextCellAppearance) {
      assertEquals(expectedAttribute, ((SimpleTextCellAppearance)appearance).getTextAttributes());
    } else if (appearance instanceof CompositeAppearance) {
      Iterator<CompositeAppearance.TextSection> iterator = ((CompositeAppearance)appearance).getSectionsIterator();
      CompositeAppearance.TextSection section = iterator.next();
      assertEquals(expectedAttribute.toTextAttributes(), section.getTextAttributes());
      assertFalse(iterator.hasNext());
    } else fail("Unknown class: " + appearance.getClass().getName());
  }

  public void testEmptyRelativePath() {
    ModifiableRootModel rootModel = createRootModel();
    VirtualFile vFile = getTempDir().createVirtualDir();
    ContentEntry contentEntry = rootModel.addContentEntry(vFile);
    CellAppearanceEx appearance = myService.forContentFolder(contentEntry.addSourceFolder(vFile, false));
    assertEquals("." + File.separator, appearance.getText());
  }

  public void testNamedLibrary() {
    ModifiableRootModel rootModel = createRootModel();
    Library library = rootModel.getModuleLibraryTable().createLibrary("named");
    CellAppearanceEx appearance = simpleTextAppearance(getLastOrderEntry(rootModel));
    assertEquals("named", appearance.getText());
    addClassRoot(library);
    appearance = simpleTextAppearance(getLastOrderEntry(rootModel));
    assertEquals("named", appearance.getText());
  }

  public void testSingleUrlLibrary() {
    ModifiableRootModel rootModel = createRootModel();
    Library library = rootModel.getModuleLibraryTable().createLibrary();
    VirtualFile virtualFile = addClassRoot(library);
    ValidFileCellAppearance appearance = (ValidFileCellAppearance)myService.forLibrary(myProject, library, false);
    assertEquals(virtualFile, appearance.getFile());
  }

  public void testRenderAbsentJdk() {
    SimpleTextCellAppearance appearance = (SimpleTextCellAppearance)myService.forJdk(null, false, false, true);
    assertEquals("<No SDK>", appearance.getText());
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, appearance.getTextAttributes());
  }

  private VirtualFile addClassRoot(Library library) {
    VirtualFile vFile = getTempDir().createVirtualFile("yyy");
    addClassesRoot(library, vFile);
    return vFile;
  }

  public static void addClassesRoot(Library library, VirtualFile vFile) {
    final Library.ModifiableModel modifiableModel = library.getModifiableModel();
    modifiableModel.addRoot(vFile.getUrl(), OrderRootType.CLASSES);
    ApplicationManager.getApplication().runWriteAction(() -> modifiableModel.commit());
  }

  private static OrderEntry getLastOrderEntry(ModifiableRootModel rootModel) {
    OrderEntry[] orders = rootModel.getOrderEntries();
    return orders[orders.length - 1];
  }

  private ModifiableRootModel createRootModel() {
    Module module = createTempModule();
    ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    Disposer.register(module, () -> rootModel.dispose());
    return rootModel;
  }

  private CellAppearanceEx simpleTextAppearance(OrderEntry orderEntry) {
    return myService.forOrderEntry(myProject, orderEntry, false);
  }

  private static class MockOrderEntry implements OrderEntry {
    private final String myName;
    private final boolean myValid;

    MockOrderEntry(String name, boolean valid) {
      myName = name;
      myValid = valid;
    }

    @Override
    public VirtualFile @NotNull [] getFiles(@NotNull OrderRootType type) {
      notImplemented();
      return null;
    }

    private static void notImplemented() {
      fail("Not implemented");
    }

    @NotNull
    @Override
    public String getPresentableName() {
      return myName;
    }

    @Override
    public boolean isValid() {
      return myValid;
    }

    @Override
    @NotNull
    public Module getOwnerModule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <R> R accept(@NotNull RootPolicy<R> policy, R initialValue) {
      return policy.visitOrderEntry(this, initialValue);
    }

    @Override
    public String @NotNull [] getUrls(@NotNull OrderRootType rootType) {
      notImplemented();
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    @Override
    public boolean isSynthetic() {
      return false;
    }

    @Override
    public int compareTo(@NotNull OrderEntry orderEntry) {
      notImplemented();
      return 0;
    }
  }
}
