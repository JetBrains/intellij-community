/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.roots;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;

public class ManagingContentRootsTest extends JavaProjectTestCase {
  private VirtualFile dir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        LocalFileSystem fs = LocalFileSystem.getInstance();
        dir = fs.refreshAndFindFileByIoFile(createTempDirectory());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public void testCreationOfContentRootWithFile() {
    VirtualFile root = createChildDirectory(dir, "root");
    String url = root.getUrl();

    PsiTestUtil.addContentRoot(myModule, root);


    assertEquals(root, findContentEntry(url).getFile());

    delete(root);
    assertNotNull(findContentEntry(url));

    root = createChildDirectory(dir, "root");
    assertEquals(root, findContentEntry(url).getFile());
  }

  public void testCreationOfContentRootWithUrl() {
    VirtualFile root = createChildDirectory(dir, "root");
    String url = root.getUrl();
    String path = root.getPath();
    delete(root);

    addContentRoot(path);

    assertNotNull(findContentEntry(url));

    root = createChildDirectory(dir, "root");
    assertEquals(root, findContentEntry(url).getFile());
  }

  public void testCreationOfContentRootWithUrlWhenFileExists() {
    VirtualFile root = createChildDirectory(dir, "root");
    addContentRoot(root.getPath());
    assertEquals(root, findContentEntry(root.getUrl()).getFile());
  }

  public void testGettingModifiableModelCorrectlySetsRootModelForContentEntries() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiTestUtil.addContentRoot(myModule, dir);

      ModifiableRootModel m = getRootManager().getModifiableModel();
      ContentEntry e = findContentEntry(dir.getUrl(), m);

      assertSame(m, e.getRootModel());
      m.dispose();
    });
  }

  public void testAddExcludePattern() {
    PsiTestUtil.addContentRoot(myModule, dir);
    ModuleRootModificationUtil.updateModel(myModule, model -> findContentEntry(dir.getUrl(), model).addExcludePattern("*.txt"));
    assertEquals("*.txt", assertOneElement(findContentEntry(dir.getUrl()).getExcludePatterns()));
    ModuleRootModificationUtil.updateModel(myModule, model -> findContentEntry(dir.getUrl(), model).removeExcludePattern("*.txt"));
    assertEmpty(findContentEntry(dir.getUrl()).getExcludePatterns());
  }

  public void testExcludePatternSerialization() throws Exception {
    PsiTestUtil.addContentRoot(myModule, dir);
    ModuleRootModificationUtil.updateModel(myModule, model -> findContentEntry(dir.getUrl(), model).addExcludePattern("exc"));
    StoreUtil.saveDocumentsAndProjectSettings(myProject);

    Element root = JDOMUtil.load(new File(myModule.getModuleFilePath()));
    String elementText = "<content url=\"file://$MODULE_DIR$/idea_test_\">\n" +
                         "  <excludePattern pattern=\"exc\" />\n" +
                         "</content>";
    assertEquals(elementText, JDOMUtil.writeElement(root.getChild("component").getChild("content")));
  }

  public void testExcludePatternDeserialization() throws Exception {
    File dir = createTempDir("module");
    String dirUrl = VfsUtilCore.fileToUrl(dir);

    File iml = new File(dir, "module.iml");
    FileUtil.writeToFile(
      iml,
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<module type=\"JAVA_MODULE\" version=\"4\">\n" +
      "  <component name=\"NewModuleRootManager\">\n" +
      "    <content url=\"" + dirUrl + "\">\n" +
      "      <excludePattern pattern=\"exc\" />\n" +
      "    </content>\n" +
      "  </component>\n" +
      "</module>");

    Module module = WriteAction.computeAndWait(() -> ModuleManager.getInstance(myProject).loadModule(iml.getPath()));
    assertEquals("exc", assertOneElement(findContentEntry(dirUrl, ModuleRootManager.getInstance(module)).getExcludePatterns()));
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
    ModuleRootModificationUtil.addContentRoot(getModule(), path);
  }

  private ModuleRootManager getRootManager() {
    return ModuleRootManager.getInstance(myModule);
  }
}
