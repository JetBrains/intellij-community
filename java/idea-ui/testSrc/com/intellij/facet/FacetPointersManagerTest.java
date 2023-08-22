// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.facet.impl.pointers.FacetPointersManagerImpl;
import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetType;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointerListener;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class FacetPointersManagerTest extends FacetTestCase {
  public void testCreatePointerFromFacet()  {
    final MockFacet mockFacet = addFacet();
    final FacetPointer<MockFacet> pointer = getManager().create(mockFacet);
    assertRefersTo(pointer, mockFacet);

    assertTrue(getManager().isRegistered(pointer));
    removeFacet(mockFacet);
    assertFalse(getManager().isRegistered(pointer));
  }

  public void testCreatePointerById()  {
    final FacetPointer<Facet> pointer = getManager().create(getId(MockFacetType.getInstance().getStringId(), "myFacet"));
    assertNull(pointer.getFacet());
    assertEquals(myModule.getName(), pointer.getModuleName());
    assertEquals("myFacet", pointer.getFacetName());
    FacetType<?, ?> expected = MockFacetType.getInstance();
    assertSame(expected, pointer.getFacetType());

    final MockFacet mockFacet = addFacet("myFacet");
    assertRefersTo(pointer, mockFacet);
  }

  public void testResolveAfterRenaming()  {
    final FacetPointer<Facet> pointer = getManager().create(getId(MockFacetType.getInstance().getStringId(), "myFacet"));
    assertNull(pointer.getFacet());

    final MockFacet mockFacet = addFacet("facet");
    assertNull(pointer.getFacet());

    renameFacet(mockFacet, "myFacet");
    assertRefersTo(pointer, mockFacet);
  }

  public void testRenameFacetForResolvedPointer()  {
    final MockFacet mockFacet = addFacet("facet");
    final FacetPointer<MockFacet> pointer = getManager().create(mockFacet);
    assertRefersTo(pointer, mockFacet);

    renameFacet(mockFacet, "newName");
    assertRefersTo(pointer, mockFacet);
  }

  public void testRenameFacetForUnresolvedPointer() {
    final FacetPointer<Facet> pointer = getManager().create(getId(MockFacetType.getInstance().getStringId(), "myFacet"));
    assertNull(pointer.getFacet());
    MockFacet facet = addFacet("myFacet");
    renameFacet(facet, "newName");
    assertRefersTo(pointer, facet);
  }

  public void testRenameModuleForUnresolvedPointer() throws ModuleWithNameAlreadyExists {
    FacetPointer<Facet> pointer = getManager().create(FacetPointersManager.constructId("newModule", MockFacetType.getInstance().getStringId(), "myFacet"));
    assertNull(pointer.getFacet());
    final Module module = createModule("newModule");
    ApplicationManager.getApplication().runWriteAction(() -> {
      FacetManager.getInstance(module).addFacet(MockFacetType.getInstance(), "myFacet", null);
    });

    renameModule(module, "newModule2");
    assertEquals("newModule2", pointer.getModuleName());
    assertNotNull(pointer.getFacet());
  }

  public void testRenameUnresolved()  {
    final MockFacet facet = addFacet("facet");
    final FacetPointer<Facet> pointer = getManager().create(getId(MockFacetType.getInstance().getStringId(), "facet"));
    renameFacet(facet, "newName");
    assertRefersTo(pointer, facet);
  }

  public void testRenameModule() throws ModuleWithNameAlreadyExists {
    final FacetPointer<Facet> pointer = getManager().create(getId(MockFacetType.getInstance().getStringId(), "facet"));
    final MockFacet mockFacet = addFacet("facet");
    assertSame(mockFacet, pointer.getFacet());
    assertEquals(myModule.getName(), pointer.getModuleName());

    renameModule(myModule, "newName");

    assertSame(mockFacet, pointer.getFacet());
    assertEquals("newName", pointer.getModuleName());
  }

  private void renameModule(final Module module, final String newName) throws ModuleWithNameAlreadyExists {
    WriteAction.runAndWait(() -> {
      final ModifiableModuleModel model = ModuleManager.getInstance(myProject).getModifiableModel();
      model.renameModule(module, newName);
      model.commit();
    });
  }

  public void testListeners()  {
    final MockFacet mockFacet = addFacet("facet");
    final FacetPointer<MockFacet> pointer = getManager().create(mockFacet);

    final Facet subFacet = addSubFacet(mockFacet, "subFacet");
    final FacetPointer<Facet> subPointer = getManager().create(subFacet);

    final MyFacetPointerListener<Facet> allPointersListener = new MyFacetPointerListener<>();
    getManager().addListener(allPointersListener, getTestRootDisposable());
    final MyFacetPointerListener<MockFacet> mockPointersListener = new MyFacetPointerListener<>();
    getManager().addListener(MockFacet.class, mockPointersListener, getTestRootDisposable());

    renameFacet(mockFacet, "newName");
    assertSame(pointer, assertOneElement(allPointersListener.getChanged()));
    assertSame(pointer, assertOneElement(mockPointersListener.getChanged()));

    renameFacet(subFacet, "newSub");

    assertSame(subPointer, assertOneElement(allPointersListener.getChanged()));
    assertEquals(0, mockPointersListener.getChanged().length);
  }

  private void assertRefersTo(final FacetPointer<?> pointer, final Facet facet) {
    assertSame(facet, pointer.getFacet());
    assertEquals(myModule.getName(), pointer.getModuleName());
    assertEquals(facet.getName(), pointer.getFacetName());
    assertSame(facet.getType(), pointer.getFacetType());
    assertEquals(getId(facet.getType().getStringId(), facet.getName()), pointer.getId());
  }

  private String getId(String facetTypeId, String facetName) {
    return FacetPointersManager.constructId(myModule.getName(), facetTypeId, facetName);
  }

  private FacetPointersManagerImpl getManager() {
    return (FacetPointersManagerImpl)FacetPointersManager.getInstance(getProject());
  }

  private static class MyFacetPointerListener<F extends Facet> implements FacetPointerListener<F> {
    private final Set<FacetPointer<?>> myChanged = new HashSet<>();

    @Override
    public void pointerIdChanged(@NotNull final FacetPointer<F> facetPointer, @NotNull final String oldId) {
      myChanged.add(facetPointer);
    }

    public FacetPointer<Facet>[] getChanged() {
      final FacetPointer[] pointers = myChanged.toArray(new FacetPointer[0]);
      myChanged.clear();
      return pointers;
    }
  }
}
