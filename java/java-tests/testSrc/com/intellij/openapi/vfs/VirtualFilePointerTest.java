package com.intellij.openapi.vfs;

import com.intellij.concurrency.JobUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.impl.VirtualFilePointerImpl;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 *  @author dsl
 */
public class VirtualFilePointerTest extends IdeaTestCase {
  private VirtualFilePointerManager myVirtualFilePointerManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myVirtualFilePointerManager = VirtualFilePointerManager.getInstance();
  }

  private static class LoggingListener implements VirtualFilePointerListener {
    private final ArrayList<String> myLog = new ArrayList<String>();

    @Override
    public void beforeValidityChanged(VirtualFilePointer[] pointers) {
      verifyPointersInCorrectState(pointers);
      myLog.add(buildMessage("before", pointers));
    }

    private static String buildMessage(@NonNls final String startMsg, VirtualFilePointer[] pointers) {
      StringBuilder buffer = new StringBuilder(startMsg);
      buffer.append(":");
      for (int i = 0; i < pointers.length; i++) {
        VirtualFilePointer pointer = pointers[i];
        final String s = Boolean.toString(pointer.isValid());
        if (i > 0) buffer.append(":");
        buffer.append(s);
      }
      return buffer.toString();
    }

    @Override
    public void validityChanged(VirtualFilePointer[] pointers) {
      verifyPointersInCorrectState(pointers);
      myLog.add(buildMessage("after", pointers));
    }

    public ArrayList<String> getLog() {
      return myLog;
    }
  }

  public void testDelete() throws Exception {
    File tempDirectory = createTempDirectory();
    final File fileToDelete = new File(tempDirectory, "toDelete.txt");
    fileToDelete.createNewFile();
    final LoggingListener fileToDeleteListener = new LoggingListener();
    final VirtualFilePointer fileToDeletePointer = createPointerByFile(fileToDelete, fileToDeleteListener);
    assertTrue(fileToDeletePointer.isValid());
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final VirtualFile virtualFile = getVirtualFile(fileToDelete);
        try {
          virtualFile.delete(null);
        } catch (IOException e) {
          fail();
        }
      }
    });
    assertFalse(fileToDeletePointer.isValid());
    assertEquals("[before:true, after:false]", fileToDeleteListener.getLog().toString());
    myFilesToDelete.add(tempDirectory);
  }

  public void testCreate() throws Exception {
    final File tempDirectory = createTempDirectory();
    final File fileToCreate = new File(tempDirectory, "toCreate.txt");
    final LoggingListener fileToCreateListener = new LoggingListener();
    final VirtualFilePointer fileToCreatePointer = createPointerByFile(fileToCreate, fileToCreateListener);
    assertFalse(fileToCreatePointer.isValid());
    fileToCreate.createNewFile();
    final Runnable postRunnable = new Runnable() {
      @Override
      public void run() {
        assertTrue(fileToCreatePointer.isValid());
        assertEquals("[before:false, after:true]", fileToCreateListener.getLog().toString());
        try {
          String expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, fileToCreate.getCanonicalPath().replace(File.separatorChar, '/'));
          assertEquals(expectedUrl.toUpperCase(), fileToCreatePointer.getUrl().toUpperCase());
        } catch (IOException e) {
          fail();
        }
      }
    };
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        VirtualFileManager.getInstance().refresh(false);
        final VirtualFile virtualFile = getVirtualFile(tempDirectory);
        virtualFile.refresh(false, true);
      }
    });
    postRunnable.run();
    myFilesToDelete.add(fileToCreate);
    myFilesToDelete.add(tempDirectory);
  }

  public void testMove() throws Exception {
    File tempDirectory = createTempDirectory();
    final File moveTarget = new File(tempDirectory, "moveTarget");
    moveTarget.mkdir();
    final File fileToMove = new File(tempDirectory, "toMove.txt");
    fileToMove.createNewFile();

    final LoggingListener fileToMoveListener = new LoggingListener();
    final VirtualFilePointer fileToMovePointer = createPointerByFile(fileToMove, fileToMoveListener);
    assertTrue(fileToMovePointer.isValid());
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final VirtualFile virtualFile = getVirtualFile(fileToMove);
        assertTrue(virtualFile.isValid());
        final VirtualFile target = getVirtualFile(moveTarget);
        assertTrue(target.isValid());
        try {
          virtualFile.move(null, target);
        } catch (IOException e) {
          fail();
        }
      }
    });
    assertTrue(fileToMovePointer.isValid());
    assertEquals("[]", fileToMoveListener.getLog().toString());
    final File fileAfterMove = new File(moveTarget, fileToMove.getName());
    myFilesToDelete.add(fileAfterMove);
    myFilesToDelete.add(moveTarget);
    myFilesToDelete.add(tempDirectory);
  }

  public void testCreate1() throws Exception {
    final File tempDirectory = createTempDirectory();
    final File fileToCreate = new File(tempDirectory, "toCreate1.txt");
    final LoggingListener fileToCreateListener = new LoggingListener();
    final VirtualFilePointer fileToCreatePointer = createPointerByFile(fileToCreate, fileToCreateListener);
    assertFalse(fileToCreatePointer.isValid());
    fileToCreate.createNewFile();
    final Runnable postRunnable = new Runnable() {
      @Override
      public void run() {
        assertTrue(fileToCreatePointer.isValid());
        assertEquals("[before:false, after:true]", fileToCreateListener.getLog().toString());
        try {
          String expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, fileToCreate.getCanonicalPath().replace(File.separatorChar, '/'));
          assertEquals(expectedUrl.toUpperCase(), fileToCreatePointer.getUrl().toUpperCase());
        } catch (IOException e) {
          fail();
        }
      }
    };
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        VirtualFileManager.getInstance().refresh(false);
        final VirtualFile virtualFile = getVirtualFile(tempDirectory);
        virtualFile.refresh(false, true);
      }
    });
    postRunnable.run();
    myFilesToDelete.add(fileToCreate);
    myFilesToDelete.add(tempDirectory);
  }

  public void testMultipleNotifications() throws Exception {
    final File tempDir = createTempDirectory();
    final File file_f1 = new File(tempDir, "f1");
    final File file_f2 = new File(tempDir, "f2");
    final LoggingListener listener = new LoggingListener();
    final VirtualFilePointer pointer_f1 = createPointerByFile(file_f1, listener);
    final VirtualFilePointer pointer_f2 = createPointerByFile(file_f2, listener);
    assertFalse(pointer_f1.isValid());
    assertFalse(pointer_f2.isValid());
    file_f1.createNewFile();
    file_f2.createNewFile();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        LocalFileSystem.getInstance().refresh(false);
      }
    });
    assertEquals("[before:false:false, after:true:true]", listener.getLog().toString());
    myFilesToDelete.add(file_f1);
    myFilesToDelete.add(file_f2);
  }

  public void testJars() throws Exception {
    final File tempDir = createTempDirectory();
    final File jarParent = new File(tempDir, "jarParent");
    jarParent.mkdir();
    final File jar = new File(jarParent, "x.jar");
    final File originalJar = new File(PathManagerEx.getTestDataPath() + "/psi/generics22/collect-2.2.jar".replace('/', File.separatorChar));
    FileUtil.copy(originalJar, jar);

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar); // Make sure we receive events when jar changes

    final VirtualFilePointer[] pointersToWatch = new VirtualFilePointer[2];
    final VirtualFilePointerListener listener = new VirtualFilePointerListener() {
      @Override
      public void beforeValidityChanged(VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }

      @Override
      public void validityChanged(VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }
    };
    final VirtualFilePointer jarParentPointer = createPointerByFile(jarParent, listener);
    final String pathInJar = jar.getPath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
    final String jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL,
                                                          pathInJar);
    final VirtualFilePointer jarPointer = myVirtualFilePointerManager.create(jarUrl, myProject, listener);
    pointersToWatch[0] = jarParentPointer;
    pointersToWatch[1] = jarPointer;
    assertTrue(jarParentPointer.isValid());
    assertTrue(jarPointer.isValid());

    jar.delete();
    jarParent.delete();
    refreshVFS();

    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarParentPointer.isValid());
    assertFalse(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();

    jarParent.mkdir();
    FileUtil.copy(originalJar, jar);

    refreshVFS();

    verifyPointersInCorrectState(pointersToWatch);
    assertTrue(jarParentPointer.isValid());
    assertTrue(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();

    jar.delete();
    jarParent.delete();
    refreshVFS();
    UIUtil.dispatchAllInvocationEvents();

    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarParentPointer.isValid());
    assertFalse(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testJars2() throws Exception {
    final File tempDir = createTempDirectory();
    final File jarParent = new File(tempDir, "jarParent");
    jarParent.mkdir();
    final File jar = new File(jarParent, "x.jar");
    final File originalJar = new File(PathManagerEx.getTestDataPath() + "/psi/generics22/collect-2.2.jar".replace('/', File.separatorChar));
    FileUtil.copy(originalJar, jar);

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar); // Make sure we receive events when jar changes

    final VirtualFilePointer[] pointersToWatch = new VirtualFilePointer[1];
    final VirtualFilePointerListener listener = new VirtualFilePointerListener() {
      @Override
      public void beforeValidityChanged(VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }

      @Override
      public void validityChanged(VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }
    };
    final String pathInJar = jar.getPath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
    final String jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL,
                                                          pathInJar);
    final VirtualFilePointer jarPointer = myVirtualFilePointerManager.create(jarUrl, myProject, listener);
    pointersToWatch[0] = jarPointer;
    assertTrue(jarPointer.isValid());

    jar.delete();
    refreshVFS();

    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();

    jarParent.mkdir();
    FileUtil.copy(originalJar, jar);

    refreshVFS();

    verifyPointersInCorrectState(pointersToWatch);
    assertTrue(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();

    jar.delete();
    refreshVFS();
    UIUtil.dispatchAllInvocationEvents();

    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();
  }

  private static void refreshVFS() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        VirtualFileManager.getInstance().refresh(false);
      }
    });
    UIUtil.dispatchAllInvocationEvents();
  }

  private static void verifyPointersInCorrectState(VirtualFilePointer[] pointers) {
    for (VirtualFilePointer pointer : pointers) {
      final VirtualFile file = pointer.getFile();
      assertTrue(file == null || file.isValid());
    }
  }

  private VirtualFilePointer createPointerByFile(final File file, final VirtualFilePointerListener fileListener) throws IOException {
    final VirtualFile[] vFile = new VirtualFile[1];
    final String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, file.getCanonicalPath().replace(File.separatorChar, '/'));
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        vFile[0] = VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
      }
    });
    final VirtualFilePointer fileToDeletePointer;
    if (vFile[0] == null) {
      fileToDeletePointer = myVirtualFilePointerManager.create(url, myProject, fileListener);
    }
    else {
      fileToDeletePointer = myVirtualFilePointerManager.create(vFile[0], myProject, fileListener);
    }
    return fileToDeletePointer;
  }

  public void testFilePointerUpdate() throws Exception {
    final File tempDir = createTempDirectory();
    final File file_f1 = new File(tempDir, "f1");

    final VirtualFilePointer pointer_f1 = createPointerByFile(file_f1, null);

    assertFalse(pointer_f1.isValid());

    file_f1.createNewFile();

    doVfsRefresh();

    assertTrue(pointer_f1.isValid());

    file_f1.delete();

    doVfsRefresh();
    assertFalse(pointer_f1.isValid());
  }

  public void testContainerDeletePerformance() throws Exception {
    PlatformTestUtil.startPerformanceTest(3000, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        Disposable parent = Disposer.newDisposable();
        for (int i = 0; i < 10000; i++) {
          myVirtualFilePointerManager.createContainer(parent);
        }
        Disposer.dispose(parent);
      }
    }).cpuBound().assertTiming();
  }

  private static void doVfsRefresh() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        LocalFileSystem.getInstance().refresh(false);
      }
    });
  }

  public void testThreads() throws IOException, InterruptedException {
    final File ioTempDir = createTempDirectory();
    final File ioPtrBase = new File(ioTempDir, "parent");
    final File ioPtr = new File(ioPtrBase, "f1");
    final File ioSand = new File(ioTempDir, "sand");
    final File ioSandPtr = new File(ioSand, "f2");
    ioSandPtr.getParentFile().mkdirs();
    ioSandPtr.createNewFile();
    ioPtr.getParentFile().mkdirs();
    ioPtr.createNewFile();

    doVfsRefresh();
    final VirtualFilePointer pointer = createPointerByFile(ioPtr, null);
    assertTrue(pointer.isValid());
    final VirtualFile virtualFile = pointer.getFile();
    assertNotNull(virtualFile);
    assertTrue(virtualFile.isValid());

    VirtualFileAdapter listener = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent event) {
        doit(pointer);
      }

      @Override
      public void fileDeleted(VirtualFileEvent event) {
        doit(pointer);
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(listener, getTestRootDisposable());

    int N = Timings.adjustAccordingToMySpeed(1000);
    System.out.println("N = " + N);
    for (int i=0;i< N;i++) {
      assertNotNull(pointer.getFile());
      FileUtil.delete(ioPtrBase);
      doVfsRefresh();

      // ptr is now null, cached as map

      final VirtualFile v = LocalFileSystem.getInstance().findFileByIoFile(ioSandPtr);
      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() throws Throwable {
          v.delete(this); //inc FS modCount
          LocalFileSystem.getInstance().findFileByIoFile(ioSand).createChildData(this, ioSandPtr.getName());
        }
      }.execute().throwException();

      // ptr is still null

      assertTrue(ioPtrBase.mkdirs());
      assertTrue(ioPtr.createNewFile());

      doit(pointer);
      doVfsRefresh();
    }
  }

  private static void doit(final VirtualFilePointer pointer) {
    if (((VirtualFilePointerImpl)pointer).isDisposed()) return;
    boolean b = JobUtil.invokeConcurrentlyUnderProgress(Collections.nCopies(10, null), new Processor<Object>() {
      @Override
      public boolean process(Object o) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            VirtualFile file = pointer.getFile();
            if (file != null && !file.isValid()) {
              throw new IncorrectOperationException("I've caught it. I am that good");
            }
          }
        });

        return true;
      }
    }, false, null);
    assertTrue(b);
  }

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }
}
