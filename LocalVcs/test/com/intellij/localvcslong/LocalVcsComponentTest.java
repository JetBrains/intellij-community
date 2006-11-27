package com.intellij.localvcslong;


import com.intellij.ide.impl.ProjectUtil;
import com.intellij.idea.Bombed;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
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

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

@Bombed(year = 2006,
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
    assertNotNull(getProject().getComponent(LocalVcsComponent.class));
  }

  public void testUpdatingOnRootsChanges() {
    VirtualFile root = addContentRoot();

    Entry e = getLocalVcs().findEntry(root.getPath());
    assertNotNull(e);
    assertTrue(e.isDirectory());
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

  private LocalVcs getLocalVcs() {
    return getLocalVcs(getProject());
  }

  private LocalVcs getLocalVcs(Project p) {
    return p.getComponent(LocalVcsComponent.class).getLocalVcs();
  }

  private VirtualFile addContentRoot() {
    return addContentRoot(myModule);
  }

  private VirtualFile addContentRoot(Module module) {
    try {
      LocalFileSystem fs = LocalFileSystem.getInstance();
      VirtualFile root = fs.findFileByIoFile(createTempDirectory());

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
