package org.jetbrains.jps.model;

import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.*;

/**
 * @author nik
 */
public class JpsLibraryTest extends JpsModelTestCase {
  public void testAddRoot() {
    final JpsLibrary library = myModel.getProject().addLibrary("a", JpsJavaLibraryType.INSTANCE);
    library.addRoot("file://my-url", JpsOrderRootType.COMPILED);
    assertEquals("file://my-url", assertOneElement(library.getRoots(JpsOrderRootType.COMPILED)).getUrl());
  }

  public void testModifiableCopy() {
    myModel.getProject().addLibrary("a", JpsJavaLibraryType.INSTANCE);

    final JpsModel modifiableModel = myModel.createModifiableModel(new TestJpsEventDispatcher());
    final JpsLibrary modifiable = assertOneElement(modifiableModel.getProject().getLibraryCollection().getLibraries());
    modifiable.addRoot("file://my-url", JpsOrderRootType.COMPILED);
    modifiableModel.commit();

    final JpsLibrary library = assertOneElement(myModel.getProject().getLibraryCollection().getLibraries());
    assertEquals("file://my-url", assertOneElement(library.getRoots(JpsOrderRootType.COMPILED)).getUrl());
    assertEmpty(library.getRoots(JpsOrderRootType.SOURCES));
  }

  public void testCreateReferenceByLibrary() {
    final JpsLibrary library = myModel.getProject().addLibrary("l", JpsJavaLibraryType.INSTANCE);
    final JpsLibraryReference reference = library.createReference().asExternal(myModel);
    assertEquals("l", reference.getLibraryName());
    assertSame(library, reference.resolve());
  }

  public void testCreateReferenceByName() {
    JpsLibraryReference reference = JpsElementFactory.getInstance().createLibraryReference("l", myModel.getProject().createReference()).asExternal(myModel);
    assertEquals("l", reference.getLibraryName());
    assertNull(reference.resolve());

    final JpsLibrary library = myModel.getProject().addLibrary("l", JpsJavaLibraryType.INSTANCE);
    assertSame(library, reference.resolve());
  }
}
