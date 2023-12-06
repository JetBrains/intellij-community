package com.intellij.externalSystem;

import com.intellij.buildsystem.model.DeclaredDependency;
import com.intellij.buildsystem.model.unified.UnifiedDependency;
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository;
import com.intellij.psi.xml.XmlTag;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class MavenDependencyUpdaterTest extends MavenDependencyUpdaterTestBase {

  @Test
  public void testGetDependencies() {
    List<DeclaredDependency> dependencies = myModifierService.declaredDependencies(getModule("project"));
    assertNotNull(dependencies);
    assertEquals(2, dependencies.size());

    XmlTag someArtifact = findDependencyTag("somegroup", "someartifact", "1.0");
    XmlTag another = findDependencyTag("anothergroup", "anotherArtifact", "2.0");

    assertEquals(new UnifiedDependency("somegroup", "someartifact", "1.0", null), dependencies.get(0).getUnifiedDependency());
    assertEquals(new UnifiedDependency("anothergroup", "anotherArtifact", "2.0", null), dependencies.get(1).getUnifiedDependency());

    assertEquals(someArtifact, dependencies.get(0).getPsiElement());
    assertEquals(another, dependencies.get(1).getPsiElement());
  }

  @Test
  public void testAddDependency() throws IOException {
    myModifierService.addDependency(getModule("project"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }

  @Test
  public void testAddDependencyToExistingList() throws IOException {
    myModifierService.addDependency(getModule("project"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }

  @Test
  public void testRemoveDependency() throws IOException {
    myModifierService.removeDependency(getModule("project"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }

  @Test
  public void testShouldAddDependencyToManagedTag() throws IOException {
    myModifierService.addDependency(getModule("m1"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }


  @Test
  public void testShouldRemoveDependencyIfManaged() throws IOException {
    myModifierService.removeDependency(getModule("m1"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }

  @Test
  public void testUpdateDependency() throws IOException {
    myModifierService.updateDependency(getModule("project"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", "test")
    );
    assertFilesAsExpected();
  }

  @Test
  public void testUpdateDependencyNoScope() throws IOException {
    myModifierService.updateDependency(getModule("project"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", null)
    );
    assertFilesAsExpected();
  }

  @Test
  public void testUpdateDependencyRemoveScope() throws IOException {
    myModifierService.updateDependency(getModule("project"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", null)
    );
    assertFilesAsExpected();
  }

  @Test
  public void testUpdateManagedDependency() throws IOException {
    myModifierService.updateDependency(getModule("m1"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", "test")
    );
    assertFilesAsExpected();
  }

  @Test
  public void testUpdateManagedDependencyNoScope() throws IOException {
    myModifierService.updateDependency(getModule("m1"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", null)
    );
    assertFilesAsExpected();
  }

  @Test
  public void testUpdateManagedDependencyRemoveScope() throws IOException {
    myModifierService.updateDependency(getModule("m1"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", null)
    );
    assertFilesAsExpected();
  }

  @Test
  public void testUpdateDependencyWithProperty() throws IOException {
    myModifierService.updateDependency(getModule("project"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", "test")
    );
    assertFilesAsExpected();
  }

  @Test
  public void testUpdateDependencyWithPropertyNoScope() throws IOException {
    myModifierService.updateDependency(getModule("project"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", null)
    );
    assertFilesAsExpected();
  }

  @Test
  public void testUpdateDependencyWithPropertyRemoveScope() throws IOException {
    myModifierService.updateDependency(getModule("project"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", null)
    );
    assertFilesAsExpected();
  }

  @Test
  public void testAddRepository() throws IOException {
    myModifierService.addRepository(getModule("project"), new UnifiedDependencyRepository("id", "name", "https://example.com"));
    assertFilesAsExpected();
  }

  @Test
  public void testRemoveRepository() throws IOException {
    myModifierService.deleteRepository(getModule("project"), new UnifiedDependencyRepository("id", "name", "https://example.com"));
    assertFilesAsExpected();
  }
}
