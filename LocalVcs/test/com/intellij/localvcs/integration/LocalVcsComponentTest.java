package com.intellij.localvcs.integration;

import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.TempDirTestCase;
import com.intellij.mock.MockProject;
import com.intellij.openapi.project.Project;
import org.junit.Test;

import java.io.File;

public class LocalVcsComponentTest extends TempDirTestCase {
  @Test
  public void testStorageLocation() {
    Project p = new MyMockProject("projectName", "c:/projects/projectName.ipr");
    LocalVcsComponent c = new MyLocalVcsComponent("c:/idea/system", p, null);

    File expected = new File("c:/idea/system/vcs/projectName.aabb57c4");
    assertEquals(expected, c.getStorageDir());
  }

  @Test
  public void testLoadingOnStartup() {
    Project p = new MyMockProject("a", "b");
    MyStartupManager sm = new MyStartupManager();
    LocalVcsComponent c = new MyLocalVcsComponent(tempDir.getPath(), p, sm);
    c.initComponent();

    LocalVcs vcs = new LocalVcs(new Storage(c.getStorageDir()));
    vcs.createFile("file", null, null);
    vcs.apply();
    vcs.store();

    sm.runPreStartupActivity();

    assertTrue(c.getLocalVcs().hasEntry("file"));
  }

  @Test
  public void testSaving() {
    Project p = new MyMockProject("a", "b");
    MyStartupManager sm = new MyStartupManager();
    LocalVcsComponent c = new MyLocalVcsComponent(tempDir.getPath(), p, sm);
    c.initComponent();

    sm.runPreStartupActivity();

    c.getLocalVcs().createFile("file", null, null);
    c.getLocalVcs().apply();
    c.save();

    LocalVcs vcs = new LocalVcs(new Storage(c.getStorageDir()));
    assertTrue(vcs.hasEntry("file"));
  }

  @Test
  public void testSavingBeforeStartupDoesnotThrowExceptions() {
    Project p = new MyMockProject("a", "b");
    MyStartupManager sm = new MyStartupManager();
    LocalVcsComponent c = new MyLocalVcsComponent(tempDir.getPath(), p, sm);
    c.initComponent();

    try {
      c.save();
      // success
    }
    catch (Exception e) {
      // failure
      throw new RuntimeException(e);
    }
  }

  private static class MyMockProject extends MockProject {
    private String myName;
    private String myPath;

    public MyMockProject(String name, String path) {
      myName = name;
      myPath = path;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public String getProjectFilePath() {
      return myPath;
    }
  }

  private static class MyStartupManager extends StartupManagerEx {
    private Runnable myActivity;

    public void registerPreStartupActivity(Runnable r) {
      myActivity = r;
    }

    public void runPreStartupActivity() {
      myActivity.run();
    }

    public void registerStartupActivity(Runnable runnable) {
      throw new UnsupportedOperationException();
    }

    public void registerPostStartupActivity(Runnable runnable) {
      throw new UnsupportedOperationException();
    }

    public void runPostStartup(Runnable runnable) {
      throw new UnsupportedOperationException();
    }

    public void runWhenProjectIsInitialized(Runnable runnable) {
      throw new UnsupportedOperationException();
    }

    public FileSystemSynchronizer getFileSystemSynchronizer() {
      throw new UnsupportedOperationException();
    }

    public boolean startupActivityRunning() {
      throw new UnsupportedOperationException();
    }

    public boolean startupActivityPassed() {
      throw new UnsupportedOperationException();
    }
  }

  private static class MyLocalVcsComponent extends LocalVcsComponent {
    private String mySystemPath;

    public MyLocalVcsComponent(String systemPath, Project p, MyStartupManager sm) {
      super(p, sm, null, null);
      mySystemPath = systemPath;
    }

    @Override
    protected String getSystemPath() {
      return mySystemPath;
    }

    @Override
    protected void initService() {
    }

    @Override
    protected boolean isDisabled() {
      return false;
    }
  }
}
