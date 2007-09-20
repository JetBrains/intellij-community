package com.intellij.history.integration;

import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.TempDirTestCase;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.integration.stubs.StubStartupManagerEx;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.project.Project;
import static org.easymock.classextension.EasyMock.*;
import org.junit.After;
import org.junit.Test;

import java.io.File;

public class LocalHistoryComponentTest extends TempDirTestCase {
  private MyStartupManager sm;
  private LocalHistoryComponent c;
  private LocalHistoryConfiguration config = new LocalHistoryConfiguration();
  private long purgePeriod = 0;

  @After
  public void tearDown() {
    if (c != null) c.disposeComponent();
  }

  @Test
  public void testStorageLocation() {
    Project p = createProject("projectLocationHash");
    LocalHistoryComponent c = new MyLocalHistoryComponent("c:/idea/system", p, null);

    File expected = new File("c:/idea/system/LocalHistory/projectLocationHash");
    assertEquals(expected, c.getStorageDir());
  }

  @Test
  public void testLoadingOnPreStartupActivity() {
    initComponent();

    Storage s = new Storage(c.getStorageDir());
    LocalVcs vcs = new LocalVcs(s);
    long timestamp = -1;
    vcs.createFile("file", cf(""), timestamp, false);
    vcs.save();
    s.close();

    startUp();

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
    startUp();

    c.getLocalVcs().createFile("file", cf(""), -1, false);
    c.save();
    c.closeVcs();

    assertHasSavedEntry("file");
  }

  @Test
  public void testSavingOnDispose() {
    initComponent();
    startUp();

    c.getLocalVcs().createFile("file", cf(""), -1, false);
    c.disposeComponent();

    assertHasSavedEntry("file");
  }

  @Test
  public void testPurgingOnDispose() {
    initComponent();
    startUp();

    setCurrentTimestamp(10);
    c.getLocalVcs().createDirectory("1");

    setCurrentTimestamp(20);
    c.getLocalVcs().createDirectory("2");

    setCurrentTimestamp(30);
    c.getLocalVcs().createDirectory("3");

    assertEquals(3, c.getLocalVcsImpl().getChangeList().getChanges().size());

    config.PURGE_PERIOD = 5;
    c.disposeComponent();

    assertEquals(1, c.getLocalVcsImpl().getChangeList().getChanges().size());
  }

  private void assertHasSavedEntry(String path) {
    Storage s = new Storage(c.getStorageDir());
    LocalVcs vcs = new LocalVcs(s);
    s.close();
    assertTrue(vcs.hasEntry(path));
  }

  @Test
  public void testSavingAndDisposeBeforeStartupDoesNotThrowExceptions() {
    initComponent();

    try {
      c.save();
      c.disposeComponent();
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

    LocalHistoryComponent c = new LocalHistoryComponent(p, sm, null, null, null, null);

    c.initComponent();
    c.save();
    c.disposeComponent();

    verify(sm);
  }

  @Test
  public void testCleaningOnDisposeInUnitTestMode() {
    final boolean[] isUnitTestMode = new boolean[]{true};
    LocalHistoryComponent c = new LocalHistoryComponent(null, null, null, null, null, config) {
      @Override
      public File getStorageDir() {
        return new File(tempDir, "vcs");
      }

      @Override
      protected boolean isUnitTestMode() {
        return isUnitTestMode[0];
      }

      @Override
      protected void initService() {
      }

      @Override
      protected void closeService() {
      }

      @Override
      protected boolean isDefaultProject() {
        return false;
      }
    };

    c.init();
    assertTrue(c.getStorageDir().exists());

    c.disposeComponent();
    assertFalse(c.getStorageDir().exists());

    isUnitTestMode[0] = false;

    c.init();
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

  private void startUp() {
    sm.runPreStartupActivity();
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

  private class MyLocalHistoryComponent extends LocalHistoryComponent {
    private String mySystemPath;
    private boolean isVcsInitialized;

    public MyLocalHistoryComponent(String systemPath, Project p, MyStartupManager sm) {
      super(p, sm, null, null, null, config);
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
    public void closeVcs() {
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
  }
}
