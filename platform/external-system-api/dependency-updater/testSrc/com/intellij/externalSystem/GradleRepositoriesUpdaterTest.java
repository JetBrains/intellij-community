package com.intellij.externalSystem;

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository;
import org.junit.Test;

import java.io.IOException;

public class GradleRepositoriesUpdaterTest extends GradleDependencyUpdaterTestBase {

  @Test
  public void testAddRepository() throws IOException {
    importProjectFromTemplate();
    assertTrue(myModifierService.supports(getModule("project")));
    myModifierService.addRepository(getModule("project"), new UnifiedDependencyRepository("myId", "myName", "https://example.com"));
    assertScriptChanged();
  }

  @Test
  public void testDoNotAddRepositoryIfExists() throws IOException {
    importProjectFromTemplate();
    assertTrue(myModifierService.supports(getModule("project")));
    myModifierService.addRepository(getModule("project"), new UnifiedDependencyRepository("myId", "myName", "https://example.com"));
    assertScriptNotChanged();
  }

  @Test
  public void testAddCentralRepositoryAsMethodCall() throws IOException {
    importProjectFromTemplate();
    assertTrue(myModifierService.supports(getModule("project")));
    myModifierService
      .addRepository(getModule("project"), new UnifiedDependencyRepository("central", "central", "https://repo1.maven.org/maven2"));
    assertScriptChanged();
  }
}
