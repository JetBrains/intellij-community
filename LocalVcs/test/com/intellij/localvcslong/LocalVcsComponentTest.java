package com.intellij.localvcslong;


import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.localvcs.*;
import com.intellij.localvcs.integration.LocalVcsAction;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.idea.Bombed;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class LocalVcsComponentTest extends IdeaTestCase {
  private VirtualFile root;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        root = addContentRoot();
      }
    });
  }

  public void testComponentInitialitation() {
    assertNotNull(getVcsComponent());
  }

  public void testUpdatingOnRootsChanges() {
    VirtualFile root = addContentRoot();

    Entry e = getVcs().findEntry(root.getPath());
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  public void testUpdatingFilesOnRootsChanges() throws Exception {
    VirtualFile root = addContentRootWithFile("file.java", myModule);

    assertNotNull(getVcs().findEntry(root.getPath() + "/file.java"));
  }

  public void ignoreTestUpdatingOnStartup() throws Exception {
    // todo cant make idea do that i want...
    File dir = createTempDirectory();
    File projectFile = new File(dir, "project.ipr");
    Project p = ProjectManagerEx.getInstanceEx().newProject(projectFile.getPath(), false, false);
    ((StartupManagerImpl)StartupManager.getInstance(p)).runStartupActivities();

    ModifiableModuleModel model = ModuleManager.getInstance(p).getModifiableModel();
    Module m = model.newModule(new File(dir, "module.iml").getPath(), ModuleType.JAVA);
    model.commit();

    VirtualFile root = addContentRoot(m);

    p.save();
    Disposer.dispose(p);

    VirtualFile f = root.createChildData(null, "file.java");

    p = ProjectManagerEx.getInstanceEx().loadAndOpenProject(projectFile.getPath());
    ((StartupManagerImpl)StartupManager.getInstance(p)).runStartupActivities();

    ILocalVcs vcs = LocalVcsComponent.getLocalVcsFor(p);
    boolean result = vcs.hasEntry(f.getPath());

    Disposer.dispose(p);

    assertTrue(result);
  }

  public void testWorkingWithFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    Entry e = getVcs().findEntry(f.getPath());
    assertNotNull(e);
    assertFalse(e.isDirectory());
  }

  public void testIgnoringFilteredFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.class");
    assertFalse(getVcs().hasEntry(f.getPath()));
  }

  public void testSaving() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    myProject.save();

    Storage s = new Storage(getVcsComponent().getStorageDir());
    LocalVcs vcs = new LocalVcs(s);
    s.close();
    assertTrue(vcs.hasEntry(f.getPath()));
  }

  public void testProvidingContent() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    f.setBinaryContent(new byte[]{1});

    assertEquals(1, f.contentsToByteArray()[0]);

    getVcs().changeFileContent(f.getPath(), new byte[]{2}, null);
    getVcs().apply();
    assertEquals(2, f.contentsToByteArray()[0]);
  }

  public void testContentForFilteredFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.exe");
    f.setBinaryContent(new byte[]{1});

    assertFalse(getVcs().hasEntry(f.getPath()));
    assertEquals(1, f.contentsToByteArray()[0]);
  }

  public void testActions() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    f.setBinaryContent(new byte[]{0});
    setDocumentTextFor(f, new byte[]{1});

    assertEquals(0, getVcsContentOf(f)[0]);

    LocalVcsAction a = getVcsComponent().startAction("label");
    assertEquals(1, getVcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[]{2});

    a.finish();
    assertEquals(2, getVcsContentOf(f)[0]);

    List<Label> l = getVcs().getLabelsFor(f.getPath());
    assertEquals("label", l.get(0).getName());
    assertNull(l.get(1).getName());
  }

  private void setDocumentTextFor(VirtualFile f, byte[] bytes) {
    Document d = FileDocumentManager.getInstance().getDocument(f);
    String t = new String(bytes);
    d.setText(t);
  }

  private byte[] getVcsContentOf(VirtualFile f) {
    return getVcs().getEntry(f.getPath()).getContent().getBytes();
  }

  private ILocalVcs getVcs() {
    return LocalVcsComponent.getLocalVcsFor(getProject());
  }

  private LocalVcsComponent getVcsComponent() {
    return (LocalVcsComponent)LocalVcsComponent.getInstance(getProject());
  }

  private VirtualFile addContentRoot() {
    return addContentRoot(myModule);
  }

  private VirtualFile addContentRoot(Module m) {
    return addContentRootWithFile(null, m);
  }

  private VirtualFile addContentRootWithFile(String fileName, Module module) {
    try {
      LocalFileSystem fs = LocalFileSystem.getInstance();
      File dir = createTempDirectory();

      if (fileName != null) {
        File f = new File(dir, fileName);
        f.createNewFile();
      }

      VirtualFile root = fs.findFileByIoFile(dir);

      ModuleRootManager rm = ModuleRootManager.getInstance(module);
      ModifiableRootModel m = rm.getModifiableModel();
      m.addContentEntry(root);
      m.commit();

      return root;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
