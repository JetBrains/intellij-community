package com.intellij.roots.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.roots.ModuleRootManagerTestCase;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

/**
 *  @author dsl
 */
public class LibraryTest extends ModuleRootManagerTestCase {
  public void testModification() throws Exception {
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    final Library library = libraryTable.createLibrary("NewLibrary");
    final boolean[] listenerNotifiedOnChange = new boolean[1];
    library.getRootProvider().addRootSetChangedListener(new RootProvider.RootSetChangedListener() {
      @Override
      public void rootSetChanged(RootProvider wrapper) {
        listenerNotifiedOnChange[0] = true;
      }

    });
    final Library.ModifiableModel model1 = library.getModifiableModel();
    model1.addRoot("file://x.jar", OrderRootType.CLASSES);
    model1.addRoot("file://x-src.jar", OrderRootType.SOURCES);
    commit(model1);
    assertTrue(listenerNotifiedOnChange[0]);

    listenerNotifiedOnChange[0] = false;

    final Library.ModifiableModel model2 = library.getModifiableModel();
    model2.setName("library");
    commit(model2);
    assertFalse(listenerNotifiedOnChange[0]);

    final Element element = new Element("root");
    library.writeExternal(element);
    assertEquals("<root><library name=\"library\"><CLASSES><root url=\"file://x.jar\" /></CLASSES><JAVADOC /><SOURCES><root url=\"file://x-src.jar\" /></SOURCES></library></root>",
            new XMLOutputter().outputString(element));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        libraryTable.removeLibrary(library);
      }
    });
  }

  public void testAddRemoveExcludedRoot() {
    VirtualFile jar = getJDomJar();
    LibraryEx library = (LibraryEx)createLibrary("junit", jar, null);
    assertEmpty(library.getExcludedRoots());

    LibraryEx.ModifiableModelEx model = library.getModifiableModel();
    model.addExcludedRoot(jar.getUrl());
    commit(model);
    assertOrderedEquals(library.getExcludedRoots(), jar);

    LibraryEx.ModifiableModelEx model2 = library.getModifiableModel();
    model2.removeExcludedRoot(jar.getUrl());
    commit(model2);
    assertEmpty(library.getExcludedRoots());
  }

  public void testRemoveExcludedRootWhenParentRootIsRemoved() {
    VirtualFile jar = getJDomJar();
    LibraryEx library = (LibraryEx)createLibrary("junit", jar, null);

    LibraryEx.ModifiableModelEx model = library.getModifiableModel();
    VirtualFile excluded = jar.findChild("org");
    assertNotNull(excluded);
    model.addExcludedRoot(excluded.getUrl());
    commit(model);

    assertOrderedEquals(library.getExcludedRoots(), excluded);
    LibraryEx.ModifiableModelEx model2 = library.getModifiableModel();
    model2.removeRoot(jar.getUrl(), OrderRootType.CLASSES);
    commit(model2);

    assertEmpty(library.getExcludedRoots());
  }

  private static void commit(final Library.ModifiableModel modifyableModel1) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modifyableModel1.commit();
      }
    });
  }
}
