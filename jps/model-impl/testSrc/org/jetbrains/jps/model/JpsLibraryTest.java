package org.jetbrains.jps.model;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.*;

/**
 * @author nik
 */
public class JpsLibraryTest extends JpsModelTestCase {
  public void testAddLibrary() {
    JpsLibrary a = myProject.addLibrary("a", JpsJavaLibraryType.INSTANCE);
    JpsLibraryCollection collection = myProject.getLibraryCollection();
    assertSameElements(collection.getLibraries(), a);
    assertSameElements(ContainerUtilRt.newArrayList(collection.getLibraries(JpsJavaLibraryType.INSTANCE)), a);
    assertEmpty(ContainerUtilRt.newArrayList(collection.getLibraries(JpsJavaSdkType.INSTANCE)));
  }

  public void testAddRoot() {
    final JpsLibrary library = myProject.addLibrary("a", JpsJavaLibraryType.INSTANCE);
    library.addRoot("file://my-url", JpsOrderRootType.COMPILED);
    assertEquals("file://my-url", assertOneElement(library.getRoots(JpsOrderRootType.COMPILED)).getUrl());
  }

  public void testModifiableCopy() {
    myProject.addLibrary("a", JpsJavaLibraryType.INSTANCE);

    final JpsModel modifiableModel = myModel.createModifiableModel(new TestJpsEventDispatcher());
    final JpsLibrary modifiable = assertOneElement(modifiableModel.getProject().getLibraryCollection().getLibraries());
    modifiable.addRoot("file://my-url", JpsOrderRootType.COMPILED);
    modifiableModel.commit();

    final JpsLibrary library = assertOneElement(myProject.getLibraryCollection().getLibraries());
    assertEquals("file://my-url", assertOneElement(library.getRoots(JpsOrderRootType.COMPILED)).getUrl());
    assertEmpty(library.getRoots(JpsOrderRootType.SOURCES));
  }

  public void testCreateReferenceByLibrary() {
    final JpsLibrary library = myProject.addLibrary("l", JpsJavaLibraryType.INSTANCE);
    final JpsLibraryReference reference = library.createReference().asExternal(myModel);
    assertEquals("l", reference.getLibraryName());
    assertSame(library, reference.resolve());
  }

  public void testCreateReferenceByName() {
    JpsLibraryReference reference = JpsElementFactory.getInstance().createLibraryReference("l", myProject.createReference()).asExternal(myModel);
    assertEquals("l", reference.getLibraryName());
    assertNull(reference.resolve());

    final JpsLibrary library = myProject.addLibrary("l", JpsJavaLibraryType.INSTANCE);
    assertSame(library, reference.resolve());
  }
}
