// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyTestHelper;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.io.PathKt;
import org.jdom.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ManagingContentRootsTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile dir;

  @BeforeEach
  void setUp() {
    dir = VfsTestUtil.createDir(HeavyTestHelper.getOrCreateProjectBaseDir(heavyFixture.get().getProject()), "dir");
  }

  @Test
  public void testCreationOfContentRootWithFile() {
    VirtualFile root = createChildDirectory(dir, "root");
    String url = root.getUrl();

    PsiTestUtil.addContentRoot(heavyFixture.get().getModule(), root);
    assertEquals(root, findContentEntry(url).getFile());

    VfsTestUtil.deleteFile(root);
    assertNotNull(findContentEntry(url));

    root = createChildDirectory(dir, "root");
    assertEquals(root, findContentEntry(url).getFile());
  }

  @Test
  public void testCreationOfContentRootWithUrl() {
    VirtualFile root = createChildDirectory(dir, "root");
    String url = root.getUrl();
    String path = root.getPath();
    VfsTestUtil.deleteFile(root);

    addContentRoot(path);

    assertNotNull(findContentEntry(url));

    root = createChildDirectory(dir, "root");
    assertEquals(root, findContentEntry(url).getFile());
  }

  @Test
  public void testCreationOfContentRootWithUrlWhenFileExists() {
    VirtualFile root = createChildDirectory(dir, "root");
    addContentRoot(root.getPath());
    assertEquals(root, findContentEntry(root.getUrl()).getFile());
  }

  @Test
  public void testExcludePatternSerialization() throws Exception {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    PsiTestUtil.addContentRoot(fixture.getModule(), dir);
    ModuleRootModificationUtil.updateModel(fixture.getModule(), model -> findContentEntry(dir.getUrl(), model).addExcludePattern("exc"));

    StoreUtil.saveSettings(fixture.getProject());

    Element root = JDOMUtil.load(fixture.getModule().getModuleNioFile());
    String elementText = "<content url=\"file://$MODULE_DIR$/" + dir.getNameSequence() + "\">\n" +
                         "  <excludePattern pattern=\"exc\" />\n" +
                         "</content>";
    assertEquals(elementText, JDOMUtil.writeElement(root.getChild("component").getChild("content")));
  }

  @Test
  public void testExcludePatternDeserialization() throws Exception {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Path dir = fixture.createTempVirtualDir().toNioPath();
    String dirUrl = VfsUtilCore.pathToUrl(dir.toString());

    Path iml = dir.resolve("module.iml");
    PathKt.write(
      iml,
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<module type=\"JAVA_MODULE\" version=\"4\">\n" +
      "  <component name=\"NewModuleRootManager\">\n" +
      "    <content url=\"" + dirUrl + "\">\n" +
      "      <excludePattern pattern=\"exc\" />\n" +
      "    </content>\n" +
      "  </component>\n" +
      "</module>");

    Module module = WriteAction.computeAndWait(() -> ModuleManager.getInstance(fixture.getProject()).loadModule(iml));
    assertEquals("exc", assertThat(findContentEntry(dirUrl, ModuleRootManager.getInstance(module)).getExcludePatterns()).singleElement().actual());
  }

  private ContentEntry findContentEntry(String url) {
    return findContentEntry(url, getRootManager());
  }

  private static ContentEntry findContentEntry(String url, ModuleRootModel m) {
    for (ContentEntry e : m.getContentEntries()) {
      if (e.getUrl().equals(url)) return e;
    }
    return null;
  }

  private void addContentRoot(final String path) {
    ModuleRootModificationUtil.addContentRoot(heavyFixture.get().getModule(), path);
  }

  private ModuleRootManager getRootManager() {
    return ModuleRootManager.getInstance(heavyFixture.get().getModule());
  }
}
