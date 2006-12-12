package com.intellij.localvcslong;


import com.intellij.ide.impl.ProjectUtil;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.idea.Bombed;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

@Bombed(year = 2007,
        month = Calendar.NOVEMBER,
        day = 27,
        time = 19,
        user = "Anton.Makeev",
        description = "localVcs is not yet integrated")
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
    assertNotNull(getComponent());
  }

  public void testUpdatingOnRootsChanges() {
    VirtualFile root = addContentRoot();

    Entry e = getLocalVcs().findEntry(root.getPath());
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  public void testUpdatingFilesOnRootsChanges() throws Exception {
    VirtualFile root = addContentRootWithFile("file.java");

    assertNotNull(getLocalVcs().findEntry(root.getPath() + "/file.java"));
  }

  public void ignoreTestUpdatingOnStartup() throws Exception {
    // todo make it work
    File dir = createTempDirectory();
    Project p = ProjectManagerEx.getInstanceEx().newProject(dir.getPath(), true, false);
    ModifiableModuleModel model = ModuleManager.getInstance(p).getModifiableModel();
    Module m = model.newModule(new File(dir, "module.iml").getPath(), ModuleType.JAVA);
    model.commit();

    //addContentRoot(m);
    //
    //String path = p.getProjectFile().getPath();
    ProjectUtil.closeProject(p);

    //VirtualFile f = root.createChildData(null, "file");
    //p = ProjectManager.getInstance().loadAndOpenProject(path);

    //
    //assertTrue(getLocalVcs(p).hasEntry(f.getPath()));
  }

  public void testWorkingWithFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file");

    Entry e = getLocalVcs().findEntry(f.getPath());
    assertNotNull(e);
    assertFalse(e.isDirectory());
  }
  
  public void testSaving() throws Exception {
    VirtualFile f = root.createChildData(null, "file");
    myProject.save();

    Storage s = new Storage(getComponent().getStorageDir());
    LocalVcs vcs = new LocalVcs(s);
    s.close();
    assertTrue(vcs.hasEntry(f.getPath()));
  }

  private LocalVcs getLocalVcs() {
    return getComponent().getLocalVcs();
  }

  private LocalVcsComponent getComponent() {
    return getProject().getComponent(LocalVcsComponent.class);
  }

  private VirtualFile addContentRoot() {
    return addContentRootWithFile(null);
  }

  private VirtualFile addContentRootWithFile(String fileName) {
    try {
      LocalFileSystem fs = LocalFileSystem.getInstance();
      File dir = createTempDirectory();

      if (fileName != null) {
        File f = new File(dir, fileName);
        f.createNewFile();
      }

      VirtualFile root = fs.findFileByIoFile(dir);

      ModuleRootManager rm = ModuleRootManager.getInstance(myModule);
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
