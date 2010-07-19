package com.intellij.roots.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.testFramework.IdeaTestCase;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

/**
 *  @author dsl
 */
public class LibraryTest extends IdeaTestCase {
  public void testModification() throws Exception {
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    final Library library = libraryTable.createLibrary("NewLibrary");
    final boolean[] listenerNotifiedOnChange = new boolean[1];
    library.getRootProvider().addRootSetChangedListener(new RootProvider.RootSetChangedListener() {
      public void rootSetChanged(RootProvider wrapper) {
        listenerNotifiedOnChange[0] = true;
      }

    });
    final Library.ModifiableModel modifyableModel = library.getModifiableModel();
    modifyableModel.addRoot("file://x.jar", OrderRootType.CLASSES);
    modifyableModel.addRoot("file://x-src.jar", OrderRootType.SOURCES);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        modifyableModel.commit();
      }
    });
    assertTrue(listenerNotifiedOnChange[0]);

    listenerNotifiedOnChange[0] = false;

    final Library.ModifiableModel modifyableModel1 = library.getModifiableModel();
    modifyableModel1.setName("library");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        modifyableModel1.commit();
      }
    });
    assertFalse(listenerNotifiedOnChange[0]);

    final Element element = new Element("root");
    library.writeExternal(element);
    assertEquals("<root><library name=\"library\"><CLASSES><root url=\"file://x.jar\" /></CLASSES><SOURCES><root url=\"file://x-src.jar\" /></SOURCES></library></root>",
            new XMLOutputter().outputString(element));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final LibraryTable.ModifiableModel modifiableModel = libraryTable.getModifiableModel();
        modifiableModel.removeLibrary(library);
        modifiableModel.commit();
      }
    });
  }
}
