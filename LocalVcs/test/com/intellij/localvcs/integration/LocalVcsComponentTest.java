package com.intellij.localvcs.integration;

import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.TempDirTestCase;
import com.intellij.mock.MockProject;
import com.intellij.openapi.project.Project;
import org.junit.After;
import org.junit.Test;

import java.io.File;

public class LocalVcsComponentTest extends TempDirTestCase {
  private MyStartupManager sm;
  private LocalVcsComponent c;

  @After
  public void tearDown() {
    if (c != null) c.disposeComponent();
  }

  @Test
  public void testStorageLocation() {
    Project p = new MyMockProject("projectName", "c:/projects/projectName.ipr");
    LocalVcsComponent c = new MyLocalVcsComponent("c:/idea/system", p, null);

    File expected = new File("c:/idea/system/vcs/projectName.aabb57c4");
    assertEquals(expected, c.getStorageDir());
  }

  @Test
  public void testLoadingOnStartup() {
    initComponent();

    Storage s = new Storage(c.getStorageDir());
    LocalVcs vcs = new LocalVcs(s);
    vcs.createFile("file", null, null);
    vcs.apply();
    vcs.store();
    s.close();

    sm.runPreStartupActivity();

    assertTrue(c.getLocalVcs().hasEntry("file"));
  }

  private void initComponent() {
    Project p = new MyMockProject("a", "b");
    sm = new MyStartupManager();
    c = new MyLocalVcsComponent(tempDir.getPath(), p, sm);
    c.initComponent();
  }

  @Test
  public void testSaving() {
    initComponent();
    sm.runPreStartupActivity();

    c.getLocalVcs().createFile("file", null, null);
    c.getLocalVcs().apply();
    c.save();

    Storage s = new Storage(c.getStorageDir());
    LocalVcs vcs = new LocalVcs(s);
    s.close();
    assertTrue(vcs.hasEntry("file"));
  }

  @Test
  public void testSavingBeforeStartupDoesNotThrowExceptions() {
    initComponent();

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
    private boolean isVcsInitialized;

    public MyLocalVcsComponent(String systemPath, Project p, MyStartupManager sm) {
      super(p, sm, null, null);
      mySystemPath = systemPath;
    }

    @Override
    protected String getSystemPath() {
      return mySystemPath;
    }


    @Override
    protected void initVcs() {
      super.initVcs();
      isVcsInitialized = true;
    }

    @Override
    protected void closeVcs() {
      if(isVcsInitialized) super.closeVcs();
    }

    @Override
    protected void initService() {
    }

    @Override
    protected void closeService() {
    }

    @Override
    protected boolean isDisabled() {
      return false;
    }
  }
}
