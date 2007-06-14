package com.intellij.localvcs.integration;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.TempDirTestCase;
import com.intellij.localvcs.core.TestLocalVcs;
import com.intellij.localvcs.core.storage.Storage;
import com.intellij.localvcs.integration.stubs.StubStartupManagerEx;
import com.intellij.openapi.project.Project;
import static org.easymock.classextension.EasyMock.*;
import org.junit.After;
import org.junit.Test;

import java.io.File;

public class LocalHistoryComponentTest extends TempDirTestCase {
  private MyStartupManager sm;
  private LocalHistoryComponent c;

  @After
  public void tearDown() {
    if (c != null) c.disposeComponent();
  }

  @Test
  public void testStorageLocation() {
    Project p = createProject("projectLocationHash");
    LocalHistoryComponent c = new MyLocalHistoryComponent("c:/idea/system", p, null);

    File expected = new File("c:/idea/system/vcs_new/projectLocationHash");
    assertEquals(expected, c.getStorageDir());
  }

  @Test
  public void testLoadingOnPreStartupActivity() {
    initComponent();

    Storage s = new Storage(c.getStorageDir());
    LocalVcs vcs = new TestLocalVcs(s);
    vcs.createFile("file", cf(""), -1);
    vcs.save();
    s.close();

    sm.runPreStartupActivity();

    assertTrue(c.getLocalVcs().hasEntry("file"));
  }

  private void initComponent() {
    Project p = createProject("project");
    sm = new MyStartupManager();
    c = new MyLocalHistoryComponent(tempDir.getPath(), p, sm);
    c.initComponent();
  }

  @Test
  public void testSaving() {
    initComponent();
    sm.runPreStartupActivity();

    c.getLocalVcs().createFile("file", cf(""), -1);
    c.save();

    Storage s = new Storage(c.getStorageDir());
    LocalVcs vcs = new TestLocalVcs(s);
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

  @Test
  public void testDisabledForDefaultProject() {
    StartupManagerEx sm = createMock(StartupManagerEx.class);
    replay(sm);

    Project p = createMock(Project.class);
    expect(p.isDefault()).andStubReturn(true);
    replay(p);

    LocalHistoryComponent c = new LocalHistoryComponent(p, sm, null, null, null) {
      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    c.initComponent();
    c.save();
    c.disposeComponent();

    verify(sm);
  }

  @Test
  public void testCleaningOnDisposeInUnitTestMode() {
    final boolean[] isUnitTestMode = new boolean[]{true};
    LocalHistoryComponent c = new LocalHistoryComponent(null, null, null, null, null) {
      @Override
      public File getStorageDir() {
        return new File(tempDir, "vcs");
      }

      @Override
      protected boolean isUnitTestMode() {
        return isUnitTestMode[0];
      }

      @Override
      protected void closeService() {
      }

      @Override
      protected boolean isDefaultProject() {
        return false;
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    c.initVcs();
    assertTrue(c.getStorageDir().exists());

    c.disposeComponent();
    assertFalse(c.getStorageDir().exists());

    isUnitTestMode[0] = false;

    c.initVcs();
    assertTrue(c.getStorageDir().exists());

    c.disposeComponent();
    assertTrue(c.getStorageDir().exists());
  }

  private Project createProject(String locationHash) {
    Project p = createMock(Project.class);
    expect(p.isDefault()).andStubReturn(false);
    expect(p.getLocationHash()).andStubReturn(locationHash);
    replay(p);
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

  private static class MyLocalHistoryComponent extends LocalHistoryComponent {
    private String mySystemPath;
    private boolean isVcsInitialized;

    public MyLocalHistoryComponent(String systemPath, Project p, MyStartupManager sm) {
      super(p, sm, null, null, null);
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
    protected long getPurgingPeriod() {
      return 1000;
    }

    @Override
    protected void closeVcs() {
      if (isVcsInitialized) super.closeVcs();
    }

    @Override
    protected void cleanupStorageAfterTestCase() {
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
