package com.intellij.compiler;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.AssertionFailedError;
import org.apache.log4j.Level;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Jeka
 */
public abstract class CompilerTestCase extends ModuleTestCase {
  private static final int COMPILING_TIMEOUT = 2 * 60 * 1000;
  protected static final String SOURCE = "source";
  protected static final String CLASSES = "classes";
  protected static final String DATA_FILE_NAME = "data.xml";
  private static final long TIMESTAMP_DELTA = 10L;
  private final String myDataRootPath;
  private Semaphore mySemaphore;

  protected VirtualFile mySourceDir;
  protected VirtualFile myClassesDir;
  protected VirtualFile myDataDir;
  protected VirtualFile myOriginalSourceDir; // directory under testData/compiler/<group>/<testCase>/ where Java sources are located
  private CompilerTestData myData;

  protected final Set<String> myRecompiledPaths = new HashSet<>();
  protected VirtualFile myModuleRoot;
  private boolean myUsedMakeToCompile = false;

  protected CompilerTestCase(String groupName) {
    myDataRootPath = PathManagerEx.getTestDataPath() + "/compiler/" + groupName;
  }

  private void waitFor() {
    if (!mySemaphore.waitFor(COMPILING_TIMEOUT)) {
      throw new AssertionFailedError("Compilation isn't finished in " + COMPILING_TIMEOUT  + "ms");
    }
  }

  @Override
  protected void setUp() throws Exception {
    //System.out.println("================BEGIN "+getName()+"====================");
    //CompileDriver.ourDebugMode = true;
    //TranslatingCompilerFilesMonitor.ourDebugMode = true;

    mySemaphore = new Semaphore();
    EdtTestUtil.runInEdtAndWait(() -> {
      myUsedMakeToCompile = false;
      super.setUp();
      //((StartupManagerImpl)StartupManager.getInstance(myProject)).runStartupActivities();
    });
    CompilerTestUtil.enableExternalCompiler();
    CompilerTestUtil.setupJavacForTests(myProject);
    CompilerTestUtil.saveApplicationSettings();
  }

  //------------------------------------------------------------------------------------------

  protected void doTest() throws Exception {
    final String name = getTestName(true);
    final Ref<Throwable> error = Ref.create();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      //long start = System.currentTimeMillis();
      try {
        doSetup(name);
        mySemaphore.down();
        doCompile(new CompileStatusNotification() {
          @Override
          public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
            try {
              assertFalse("Code did not compile!", aborted);
              if (errors > 0) {
                final CompilerMessage[] messages = compileContext.getMessages(CompilerMessageCategory.ERROR);
                StringBuilder errorBuilder = new StringBuilder();
                for(CompilerMessage message: messages) {
                  errorBuilder.append(message.getMessage()).append("\n");
                }
                fail("Compiler errors occurred! " + errorBuilder.toString());
              }
            }
            catch (Throwable t) {
              error.set(t);
            }
            finally {
              mySemaphore.up();
            }
          }
        }, 1);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      //finally {
      //  long stop = System.currentTimeMillis();
      //  System.out.println("FirstRunnable time:"+(((double)stop-(double)start)/1000.0) + "seconds");
      //}
    }, ModalityState.NON_MODAL);

    waitFor();
    if (!error.isNull()) {
      throw new Exception(error.get());
    }
    Thread.sleep(5);

    //System.out.println("\n\n=====================SECOND PASS===============================\n\n");
    final AtomicBoolean upToDateStatus = new AtomicBoolean(false);

    ApplicationManager.getApplication().invokeAndWait(() -> {
      //long start = System.currentTimeMillis();
      try {
        ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, Exception>)() -> {
          copyTestProjectFiles(new NewFilesFilter());
          return null;
        });
        mySemaphore.down();

        final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        final List<String> generated = new ArrayList<>();
        Disposable eventDisposable = Disposer.newDisposable();
        myProject.getMessageBus().connect(eventDisposable).subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
          @Override
          public void fileGenerated(String outputRoot, String relativePath) {
            generated.add(relativePath);
          }
        });
        upToDateStatus.set(compilerManager.isUpToDate(compilerManager.createProjectCompileScope(myProject)));

        doCompile(new CompileStatusNotification() {
          @Override
          public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
            Disposer.dispose(eventDisposable);
            try {
              String prefix = FileUtil.toSystemIndependentName(myModuleRoot.getPath());
              if (!StringUtil.endsWithChar(prefix, '/')) {
                prefix += "/";
              }
              for (String p : getCompiledPathsToCheck()) {
                String path = FileUtil.toSystemIndependentName(p);
                if (FileUtil.startsWith(path, prefix)) {
                  path = path.substring(prefix.length());
                }
                myRecompiledPaths.add(path);
              }
            }
            finally {
              mySemaphore.up();
            }
          }
        }, 2);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      //finally {
      //  long stop = System.currentTimeMillis();
      //  System.out.println("FirstRunnable time:"+(((double)stop-(double)start)/1000.0) + "seconds");
      //}
    }, ModalityState.NON_MODAL);

    waitFor();
    checkResults(upToDateStatus.get());
  }

  protected void copyTestProjectFiles(VirtualFileFilter filter) throws Exception {
    copyFiles(myOriginalSourceDir, mySourceDir, filter);
  }

  protected void createTestProjectStructure(VirtualFile moduleRoot) throws Exception {
    copyTestProjectFiles(new JavaFilesFilter());
  }


  protected abstract String[] getCompiledPathsToCheck();

  // override this in order to change the compilation kind
  protected void doCompile(final CompileStatusNotification notification, int pass) {
    myUsedMakeToCompile = true;
    CompilerManager.getInstance(myProject).make(notification);
  }

  private void checkResults(final boolean upToDateStatus) {

    final String[] deleted = myData.getDeletedByMake();
    final String[] recompiled = myData.getToRecompile();

    if (myUsedMakeToCompile) {
      final boolean expectedUpToDate = deleted.length == 0 && recompiled.length == 0;
      assertEquals("Up-to-date check error: ", expectedUpToDate, upToDateStatus);
    }

    final String recompiledpathsString = buildPathsMessage(myRecompiledPaths);
    for (final String path : recompiled) {
      assertTrue("file \"" + path + "\" should be recompiled | Reported as recompiled:" + recompiledpathsString, isRecompiled(path));
    }

    if (recompiled.length != getRecompiledCount()) {
      final Set<String> extraRecompiled = new HashSet<>(myRecompiledPaths);
      extraRecompiled.removeAll(Arrays.asList(recompiled));
      for (String path : extraRecompiled) {
        fail("file \"" + path + "\" should NOT be recompiled");
      }
    }
    //assertEquals(recompiled.length, getRecompiledCount());
  }

  private static String buildPathsMessage(final Collection<String> pathsSet) {
    final StringBuilder message = new StringBuilder();
    for (String p : pathsSet) {
      message.append(" | \"").append(p).append("\"");
    }
    return message.toString();
  }

  public int getRecompiledCount() {
    return myRecompiledPaths.size();
  }

  public boolean isRecompiled(String path) {
    return myRecompiledPaths.contains(path);
  }

  protected void doSetup(final String testName) throws Exception {
    Logger.getInstance("#com.intellij.compiler").setLevel(Level.ERROR); // disable debug output from ordinary category

    CompilerWorkspaceConfiguration.getInstance(myProject).CLEAR_OUTPUT_DIRECTORY = true;

    ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Object, Exception>() {
      @Override
      public Object compute() throws Exception {
        myDataDir = getDataRootDir(testName);
        myOriginalSourceDir = myDataDir.findFileByRelativePath(getSourceDirRelativePath());

        File dir = createTempDirectory();
        myModuleRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
        mySourceDir = createSourcesDir();
        myClassesDir = createOutputDir();

        VirtualFile out = myModuleRoot.createChildDirectory(this, "out");
        CompilerProjectExtension.getInstance(myProject).setCompilerOutputUrl(out.getUrl());
        createTestProjectStructure(myModuleRoot);
        setupMainModuleRootModel();

        myFilesToDelete.add(CompilerPaths.getCompilerSystemDirectory(myProject).getParentFile());

        // load data
        //if (new File(testDataPath).exists()) {
        //  LOG.assertTrue(myDataDir != null, "Path \"" + testDataPath + "\" exists on disk but is not detected by VFS");
        //}
        myData = new CompilerTestData();
        File file = new File(myDataDir.getPath().replace('/', File.separatorChar) + File.separator + DATA_FILE_NAME);
        myData.readExternal(JDOMUtil.load(file));
        return null;
      }
    });
    PlatformTestUtil.saveProject(myProject);
  }

  protected VirtualFile createOutputDir() throws IOException {
    return myModuleRoot.createChildDirectory(this, CLASSES);
  }

  protected VirtualFile createSourcesDir() throws IOException {
    return myModuleRoot.createChildDirectory(this, SOURCE);
  }

  protected boolean shouldExcludeOutputFromProject() {
    return true;
  }

  protected void setupMainModuleRootModel() {
    final Sdk jdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    PsiTestUtil.removeAllRoots(myModule, jdk);

    final VirtualFile libDir = myDataDir.findChild("lib");
    if (libDir != null) {
      if (!libDir.isDirectory()) {
        fail(libDir.getPath() + " is expected to be a directory");
      }
      final VirtualFile[] children = libDir.getChildren();
      List<String> urls = new ArrayList<>();
      for (VirtualFile child : children) {
        if (!child.isDirectory() && (child.getName().endsWith(".jar") || child.getName().endsWith(".zip"))) {
          final String url = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, child.getPath()) + JarFileSystem.JAR_SEPARATOR;
          urls.add(url);
        }
      }
      if (!urls.isEmpty()) {
        ModuleRootModificationUtil.addModuleLibrary(myModule, "module-lib", urls, Collections.emptyList());
      }
    }
    // configure source and output path
    PsiTestUtil.addContentRoot(myModule, myModuleRoot);
    PsiTestUtil.addSourceRoot(myModule, mySourceDir);
    PsiTestUtil.setCompilerOutputPath(myModule, myClassesDir.getUrl(), false);
    PsiTestUtil.setExcludeCompileOutput(myModule, shouldExcludeOutputFromProject());
  }

  protected VirtualFile getDataRootDir(final String testName) {
    final String testDataPath = myDataRootPath.replace(File.separatorChar, '/') + "/" + testName;
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(testDataPath);
  }

  protected String getSourceDirRelativePath() {
    return ".";
  }

  protected final void copyFiles(VirtualFile dataDir, VirtualFile destDir, VirtualFileFilter filter) throws Exception {
    final boolean stampsChanged = doCopyFiles(dataDir, destDir, filter);
    if (stampsChanged) {
      // need this to ensure that the compilation start timestamp will be ahead of any stamps of copied files
      Thread.sleep(TIMESTAMP_DELTA);
    }
  }

  private boolean doCopyFiles(VirtualFile dataDir, VirtualFile destDir, VirtualFileFilter filter) throws Exception {
    VirtualFile[] children = destDir.getChildren();
    for (VirtualFile child : children) {
      if (shouldDelete(child)) {
//        System.out.println("{TestCase:} deleted " + child.getPath());
        final String path = child.getPath().replace('/', File.separatorChar);
        child.delete(this);
        assertTrue("File " + path + " should be deleted in order for the test to work properly!", !new File(path).exists());
      }
    }

    children = dataDir.getChildren();
    boolean wereStampChanges = false;
    for (VirtualFile child : children) {

      if (!filter.accept(child)) continue;
      if (child.isDirectory()) { // is a directory
        VirtualFile destChild = destDir.findChild(child.getName());
        if (destChild == null) {
          destChild = destDir.createChildDirectory(this, child.getName());
        }
        wereStampChanges |= doCopyFiles(child, destChild, filter);
      }
      else {
        String name = child.getName();
        long currentTimeStamp = -1;
        if (name.endsWith(".new")) {
          name = name.substring(0, name.length() - ".new".length());
          VirtualFile destChild = destDir.findChild(name);
          if (destChild != null && destChild.isValid()) {
//            System.out.println("Replacing " + destChild.getPath() + " with " + name + "; current timestamp is " + destChild.getPhysicalTimeStamp());
            currentTimeStamp = ((FileSystemInterface)destChild.getFileSystem()).getTimeStamp(destChild);
            final String destChildPath = destChild.getPath().replace('/', File.separatorChar);
            destChild.delete(this);
            assertTrue("File " + destChildPath + " should be deleted in order for the test to work properly!",
                       !new File(destChildPath).exists());
          }
        }

        VirtualFile newChild = VfsUtilCore.copyFile(this, child, destDir, name);
        //to ensure that compiler will threat the file as changed. On Linux system timestamp may be rounded to multiple of 1000
        final long newStamp = newChild.getTimeStamp();
        if (newStamp == currentTimeStamp) {
          wereStampChanges = true;
          ((NewVirtualFile)newChild).setTimeStamp(newStamp + TIMESTAMP_DELTA);
        }
      }
    }
    return wereStampChanges;
  }

  private boolean shouldDelete(VirtualFile file) {
    String path = VfsUtilCore.getRelativePath(file, myModuleRoot, '/');
    return myData != null && myData.shouldDeletePath(path);
  }

  private static class JavaFilesFilter implements VirtualFileFilter {
    @Override
    public boolean accept(VirtualFile file) {
      return file.isDirectory() || file.getName().endsWith(".java");
    }
  }

  private static class NewFilesFilter implements VirtualFileFilter {
    @Override
    public boolean accept(VirtualFile file) {
      return file.isDirectory() || file.getName().endsWith(".new");
    }
  }

  @Override
  protected void runBareRunnable(ThrowableRunnable<Throwable> runnable) throws Throwable {
    runnable.run();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EdtTestUtil.runInEdtAndWait(() -> {
        try {
          myRecompiledPaths.clear();
          myData = null;
          myClassesDir = null;
          myDataDir = null;
          mySourceDir = null;
          myOriginalSourceDir = null;
          CompilerTestUtil.disableExternalCompiler(myProject);
        }
        finally {
          super.tearDown();
        }
      });
    }
    finally {
      //System.out.println("================END "+getName()+"====================");
      //CompileDriver.ourDebugMode = false;
      //TranslatingCompilerFilesMonitor.ourDebugMode = false;
      clearCompilerZipFileCache();
    }
  }

  private static void clearCompilerZipFileCache() {
    // until jdk8
    try {
      Field field = Class.forName("com.sun.tools.javac.zip.ZipFileIndex").getDeclaredField("zipFileIndexCache");
      field.setAccessible(true);
      ((Map)field.get(null)).clear();
    }
    catch (Exception ignored) {
      try { // trying jdk 7
        final Method getInstance = Class.forName("com.sun.tools.javac.file.ZipFileIndexCache").getDeclaredMethod("getSharedInstance");
        getInstance.setAccessible(true);
        Object cache = getInstance.invoke(null);
        final Method clearMethod = cache.getClass().getDeclaredMethod("clearCache");
        clearMethod.setAccessible(true);
        clearMethod.invoke(cache);
      }
      catch (Exception ignored2) {
      }
    }
  }
}
