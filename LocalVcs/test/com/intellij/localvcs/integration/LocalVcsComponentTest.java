package com.intellij.localvcs.integration;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.TempDirTestCase;
import com.intellij.localvcs.integration.stubs.StubStartupManagerEx;
import com.intellij.openapi.project.Project;
import org.junit.After;
import org.junit.Test;
import org.easymock.EasyMock;

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
    Project p = createProject("projectName", "hash");
    LocalVcsComponent c = new MyLocalVcsComponent("c:/idea/system", p, null);

    File expected = new File("c:/idea/system/vcs/projectName.hash");
    assertEquals(expected, c.getStorageDir());
  }

  @Test
  public void testLoadingOnPreStartupActivity() {
    initComponent();

    Storage s = new Storage(c.getStorageDir());
    LocalVcs vcs = new LocalVcs(s);
    vcs.createFile("file", null, null);
    vcs.apply();
    vcs.save();
    s.close();

    sm.runPreStartupActivity();

    assertTrue(c.getLocalVcs().hasEntry("file"));
  }

  private void initComponent() {
    Project p = createProject("a", "b");
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

  private Project createProject(String name, String filePath) {
    Project p = EasyMock.createMock(Project.class);
    EasyMock.expect(p.getName()).andStubReturn(name);
    EasyMock.expect(p.getLocationHash()).andStubReturn(filePath);
    EasyMock.replay(p);
    return p;
  }

  private static class MyStartupManager extends StubStartupManagerEx {
    private Runnable myActivity;

    public void registerPreStartupActivity(Runnable r) {
      myActivity = r;
    }

    public void runPreStartupActivity() {
      myActivity.run();
    }
  }

  private static class MyLocalVcsComponent extends LocalVcsComponent {
    private String mySystemPath;
    private boolean isVcsInitialized;

    public MyLocalVcsComponent(String systemPath, Project p, MyStartupManager sm) {
      super(p, sm, null, null, null, null);
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
    public boolean isEnabled() {
      return true;
    }
  }
}
