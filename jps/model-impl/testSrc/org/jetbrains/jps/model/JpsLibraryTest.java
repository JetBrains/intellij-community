package org.jetbrains.jps.model;

import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsLibraryRootType;

/**
 * @author nik
 */
public class JpsLibraryTest extends JpsModelTestCase {
  public void testAddRoot() {
    final JpsLibrary library = myModel.getProject().addLibrary(JpsJavaLibraryType.INSTANCE, "a");
    library.addUrl("file://my-url", JpsLibraryRootType.COMPILED);
    assertEquals("file://my-url", assertOneElement(library.getUrls(JpsLibraryRootType.COMPILED)));
  }

  public void testModifiableCopy() {
    myModel.getProject().addLibrary(JpsJavaLibraryType.INSTANCE, "a");

    final JpsModel modifiableModel = myModel.createModifiableModel(new TestJpsEventDispatcher());
    final JpsLibrary modifiable = assertOneElement(modifiableModel.getProject().getLibraries());
    modifiable.addUrl("file://my-url", JpsLibraryRootType.COMPILED);
    modifiableModel.commit();

    final JpsLibrary library = assertOneElement(myModel.getProject().getLibraries());
    assertEquals("file://my-url", assertOneElement(library.getUrls(JpsLibraryRootType.COMPILED)));
  }

  public void testCreateReferenceByLibrary() {
    final JpsLibrary library = myModel.getProject().addLibrary(JpsJavaLibraryType.INSTANCE, "l");
    final JpsLibraryReference reference = library.createReference().asExternal(myModel);
    assertEquals("l", reference.getLibraryName());
    assertSame(library, reference.resolve());
  }

  public void testCreateReferenceByName() {
    JpsLibraryReference reference = JpsElementFactory.getInstance().createLibraryReference("l", myModel.getProject().createReference()).asExternal(myModel);
    assertEquals("l", reference.getLibraryName());
    assertNull(reference.resolve());

    final JpsLibrary library = myModel.getProject().addLibrary(JpsJavaLibraryType.INSTANCE, "l");
    assertSame(library, reference.resolve());
  }
}
