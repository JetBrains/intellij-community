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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ContentEntryImpl;
import com.intellij.openapi.roots.impl.ModuleRootManagerComponent;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class ManagingContentRootsTest extends IdeaTestCase {
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

      assertSame(m, ((ContentEntryImpl)e).getRootModel());
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

  public void testExcludePatternSerialization() {
    PsiTestUtil.addContentRoot(myModule, dir);
    ModuleRootModificationUtil.updateModel(myModule, model -> findContentEntry(dir.getUrl(), model).addExcludePattern("exc"));
    Element entry = new Element(ContentEntryImpl.ELEMENT_NAME);
    ((ContentEntryImpl)findContentEntry(dir.getUrl())).writeExternal(entry);
    String elementText = "<content url=\"" + dir.getUrl() + "\">\n" +
                         "  <excludePattern pattern=\"exc\" />\n" +
                         "</content>";
    assertThat(entry).isEqualTo(elementText);
  }

  public void testExcludePatternDeserialization() throws IOException, JDOMException {
    ModuleRootManagerImpl.ModuleRootManagerState state = new ModuleRootManagerImpl.ModuleRootManagerState();
    state.readExternal(JDOMUtil.load("<component name=\"NewModuleRootManager\">" +
                                     "  <content url=\"" + dir.getUrl() + "\">\n" +
                                     "    <excludePattern pattern=\"exc\" />\n" +
                                     "  </content>" +
                                     "</component>\n"));
    ((ModuleRootManagerComponent)getRootManager()).loadState(state);
    assertEquals("exc", assertOneElement(findContentEntry(dir.getUrl()).getExcludePatterns()));
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
