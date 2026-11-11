package com.intellij.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResourcePatternsTest {
  private static final DefaultLightProjectDescriptor DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      try {
        contentEntry.clearSourceFolders();
        VirtualFile aaaBbb = contentEntry.getFile()
          .createChildDirectory(this, "aaa")
          .createChildDirectory(this, "bbb");
        contentEntry.addSourceFolder(aaaBbb, false);
        super.configureModule(module, model, contentEntry);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private String[] oldPatterns;

  @BeforeEach
  void setUp() {
    oldPatterns = getConf().getResourceFilePatterns();
    getConf().removeResourceFilePatterns();
  }

  @AfterEach
  void tearDown() {
    if (oldPatterns == null) {
      return;
    }
    getConf().removeResourceFilePatterns();
    for (String pattern : oldPatterns) {
      getConf().addResourceFilePattern(pattern);
    }
  }

  @Test
  public void testFilePattern() {
    getConf().addResourceFilePattern("*.ttt");

    assertTrue(getConf().isResourceFile(createFile("foo/bar.ttt")));
    assertFalse(getConf().isResourceFile(createFile("foo/bar.xxx")));
  }

  @Test
  public void testWildcards() {
    getConf().addResourceFilePattern("*.t?t");
    assertTrue(getConf().isResourceFile(createFile("foo/bar.txt")));
    assertTrue(getConf().isResourceFile(createFile("foo/bar.ttt")));
    assertTrue(getConf().isResourceFile(createFile("foo/bar.tyt")));
    assertFalse(getConf().isResourceFile(createFile("foo/bar.xml")));
  }

  @Test
  public void testDirectory() {
    getConf().addResourceFilePattern("*/foo/*");
    assertTrue(getConf().isResourceFile(createFile("goo/foo/bar.ttt")));
    assertFalse(getConf().isResourceFile(createFile("goo/foo.zzz")));
    assertFalse(getConf().isResourceFile(createFile("foo/bar.ttt")));
  }

  @Test
  public void testRootDirectory() {
    getConf().addResourceFilePattern("foo/*");
    assertFalse(getConf().isResourceFile(createFile("goo/foo/bar.ttt")));
    assertFalse(getConf().isResourceFile(createFile("goo/foo.zzz")));
    assertFalse(getConf().isResourceFile(createFile("goo/foo/zzz")));
    assertTrue(getConf().isResourceFile(createFile("foo/bar.ttt")));
  }

  @Test
  public void testDoubleAsterisk() {
    getConf().addResourceFilePattern("**/foo/*");
    assertTrue(getConf().isResourceFile(createFile("foo/bar.ttt")));
    assertTrue(getConf().isResourceFile(createFile("goo/foo/bar.ttt")));
    assertFalse(getConf().isResourceFile(createFile("goo/foo.zzz")));
    assertTrue(getConf().isResourceFile(createFile("goo/foo/zzz")));
  }

  @Test
  public void testResourceRoot() {
    getConf().addResourceFilePattern("bbb:*.ttt");

    assertTrue(getConf().isResourceFile(createFile("foo/bar.ttt")));
    assertFalse(getConf().isResourceFile(createFile("foo/bar.xxx")));
    assertFalse(getConf().isResourceFile(codeInsightFixture.get().addFileToProject("aaa/ccc/xxx.ttt", "").getVirtualFile()));
  }

  private VirtualFile createFile(String path) {
    return codeInsightFixture.get().addFileToProject("aaa/bbb/" + path, "").getVirtualFile();
  }

  private CompilerConfigurationImpl getConf() {
    return ((CompilerConfigurationImpl)(CompilerConfiguration.getInstance(codeInsightFixture.get().getProject())));
  }

}
