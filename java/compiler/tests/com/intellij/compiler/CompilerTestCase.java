package com.intellij.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.ModuleTestCase;
import org.apache.log4j.Level;
import org.jdom.Document;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @author Jeka
 */
public abstract class CompilerTestCase extends ModuleTestCase {
  private static final String SOURCE = "source";
  private static final String CLASSES = "classes";
  private static final String DATA_FILE_NAME = "data.xml";
  private final String myDataRootPath;
  private final Object LOCK = new Object();
  private int mySemaphore;

  protected VirtualFile mySourceDir;
  protected VirtualFile myClassesDir;
  protected VirtualFile myDataDir;
  protected VirtualFile myOriginalSourceDir; // directory under testData/compiler/<group>/<testCase>/ where Java sources are located
  private CompilerTestData myData;

  protected final Set<String> myDeletedPaths = new HashSet<String>();
  protected final Set<String> myRecompiledPaths = new HashSet<String>();
  protected VirtualFile myModuleRoot;

  protected CompilerTestCase(String groupName) {
    myDataRootPath = PathManagerEx.getTestDataPath() + "/compiler/" + groupName;
  }

  public void up() {
    synchronized (LOCK) {
      mySemaphore++;
      LOCK.notifyAll();
    }
  }

  public void down() {
    synchronized (LOCK) {
      mySemaphore--;
      LOCK.notifyAll();
    }
  }

  public void waitFor() throws Exception {
    synchronized(LOCK) {
      if (mySemaphore > 0) {
        LOCK.wait();
      }
    }
  }

  protected void setUp() throws Exception {
    final Exception[] ex = new Exception[] {null};
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        try {
          CompilerTestCase.super.setUp();
          //((StartupManagerImpl)StartupManager.getInstance(myProject)).runStartupActivities();
        }
        catch (Exception e) {
          ex[0] = e;
        }
        catch (Throwable th) {
          ex[0] = new Exception(th);
        }
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
    CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
    compilerConfiguration.projectOpened();
    compilerConfiguration.setDefaultCompiler(compilerConfiguration.getJavacCompiler());

  }

  /*
  protected void tearDown() throws Exception {
    // wait until all caches are saved
    super.tearDown();
  }
  */

  //------------------------------------------------------------------------------------------
  protected void doTest() throws Exception {
    final String name = getTestName(true);

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        //long start = System.currentTimeMillis();
        try {
          setup(name);
          up();
          doCompile(new CompileStatusNotification() {
            public void finished(boolean aborted, int errors, int warnings) {
              try {
                assertTrue("Code compiled with errors or did not compile!", !aborted && errors == 0);
              } finally{
                down();
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
      }
    }, ModalityState.NON_MMODAL);

    waitFor();
    Thread.sleep(5);

    //System.out.println("\n\n=====================SECOND PASS===============================\n\n");

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        //long start = System.currentTimeMillis();
        try {
          final Exception[] ex = new Exception[] {null};
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                copyTestProjectFiles(new NewFilesFilter());
              }
              catch (Exception e) {
                ex[0] = e;
              }
            }
          });
          if (ex[0] != null) {
            throw ex[0];
          }
          up();
          doCompile(new CompileStatusNotification() {
            public void finished(boolean aborted, int errors, int warnings) {
              try {
                String prefix = myModuleRoot.getPath();
                if (!StringUtil.endsWithChar(prefix, '/')) {
                  prefix += "/";
                }
                String[] pathsToDelete = CompilerManagerImpl.getPathsToDelete()/*CompileManager.getInstance(myProject).getPathsToDelete()*/;
                /*
                if ("throwsListDiffersInBaseAndDerived".equals(name)) {
                  System.out.println("Original paths to delete, count: " + pathsToDelete.length);
                }
                */
                for (String aPathsToDelete : pathsToDelete) {
                  String path = aPathsToDelete.replace(File.separatorChar, '/');
                  /*
                  if ("throwsListDiffersInBaseAndDerived".equals(name)) {
                    System.out.println("path = \"" + path + "\"");
                  }
                  */
                  if (FileUtil.startsWith(path, prefix)/*path.startsWith(prefix)*/) {
                    path = path.substring(prefix.length(), path.length());
                  }
                  myDeletedPaths.add(path);
                }

                prefix = myModuleRoot.getPath();
                if (!StringUtil.endsWithChar(prefix, '/')) {
                  prefix += "/";
                }
                String[] pathsToRecompile = getCompiledPathsToCheck()/*CompileManager.getInstance(myProject).getPathsToRecompile()*/;
                for (String aPathsToRecompile : pathsToRecompile) {
                  String path = aPathsToRecompile.replace(File.separatorChar, '/');
                  if (FileUtil.startsWith(path, prefix)/*path.startsWith(prefix)*/) {
                    path = path.substring(prefix.length(), path.length());
                  }
                  myRecompiledPaths.add(path);
                }
              } finally{
                down();
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
      }
    }, ModalityState.NON_MMODAL);

    waitFor();
    checkResults(name);
  }

  protected void copyTestProjectFiles(VirtualFileFilter filter) throws Exception {
    copyFiles(myOriginalSourceDir, mySourceDir, filter);
  }

  protected void createTestProjectStructure(VirtualFile moduleRoot) throws Exception {
    copyTestProjectFiles(new JavaFilesFilter());
  }


  protected String[] getCompiledPathsToCheck() {
    return CompilerManagerImpl.getPathsToRecompile();
  }

  // override this in order to change the compilation kind
  protected void doCompile(final CompileStatusNotification notification, int pass) {
    CompilerManager.getInstance(myProject).make(notification);
  }

  private void checkResults(final String testName) {

    final String[] deleted = myData.getDeletedByMake();
    final String deletedPathsString = buildPathsMessage(myDeletedPaths);
    for (final String path : deleted) {
      assertTrue("file \"" + path + "\" should be deleted. | Reported as deleted:" + deletedPathsString, isDeleted(path));
    }
    assertEquals(deleted.length, getDeletedCount());

    final String[] recompiled = myData.getToRecompile();
    final String recompiledpathsString = buildPathsMessage(myRecompiledPaths);
    for (final String path : recompiled) {
      assertTrue("file \"" + path + "\" should be recompiled | Reported as recompiled:" + recompiledpathsString, isRecompiled(path));
    }

    if (recompiled.length != getRecompiledCount()) {
      final Set<String> extraRecompiled = new HashSet<String>();
      extraRecompiled.addAll(myRecompiledPaths);
      extraRecompiled.removeAll(Arrays.asList(recompiled));
      for (String path : extraRecompiled) {
        assertTrue("file \"" + path + "\" should NOT be recompiled", false);
      }
    }
    //assertEquals(recompiled.length, getRecompiledCount());
  }

  private String buildPathsMessage(final Collection<String> pathsSet) {
    final StringBuffer message = new StringBuffer();
    for (String p : pathsSet) {
      message.append(" | \"").append(p).append("\"");
    }
    return message.toString();
  }

  public int getDeletedCount() {
    return myDeletedPaths.size();
  }

  public int getRecompiledCount() {
    return myRecompiledPaths.size();
  }

  public boolean isDeleted(String path) {
    return myDeletedPaths.contains(path);
  }

  public boolean isRecompiled(String path) {
    return myRecompiledPaths.contains(path);
  }

  private void setup(final String testName) throws Exception {
    Logger.getInstance("#com.intellij.compiler").setLevel(Level.ERROR); // disable debug output from ordinary category

    CompilerManagerImpl.testSetup();

    CompilerWorkspaceConfiguration.getInstance(myProject).CLEAR_OUTPUT_DIRECTORY = true;

    final Exception[] ex = new Exception[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {

        try{
          myDataDir = getDataRootDir(testName);
          myOriginalSourceDir = myDataDir.findFileByRelativePath(getSourceDirRelativePath());

          File dir = createTempDir("compiler" + testName.toUpperCase() + "_");
          myModuleRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
          mySourceDir = myModuleRoot.createChildDirectory(this, SOURCE);
          myClassesDir = myModuleRoot.createChildDirectory(this, CLASSES);

          createTestProjectStructure(myModuleRoot);
          setupMainModuleRootModel();

          myFilesToDelete.add(CompilerPaths.getCompilerSystemDirectory(myProject));

          // load data
          //if (new File(testDataPath).exists()) {
          //  LOG.assertTrue(myDataDir != null, "Path \"" + testDataPath + "\" exists on disk but is not detected by VFS");
          //}
          myData = new CompilerTestData();
          File file = new File(myDataDir.getPath().replace('/', File.separatorChar) + File.separator + DATA_FILE_NAME);
          Document document = JDOMUtil.loadDocument(file);
          myData.readExternal(document.getRootElement());

        }
        catch(Exception e){
          ex[0] = e;
        }
      }
    });

    if (ex[0] != null){
      throw ex[0];
    }
  }

  protected void setupMainModuleRootModel() {
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    rootModel.clear();

    final VirtualFile libDir = myDataDir.findChild("lib");
    if (libDir != null) {
      if (!libDir.isDirectory()) {
        assertTrue(libDir.getPath() + " is expected to be a directory", false);
      }
      final VirtualFile[] children = libDir.getChildren();
      final List<VirtualFile> jars = new ArrayList<VirtualFile>();
      for (VirtualFile child : children) {
        if (!child.isDirectory() && (child.getName().endsWith(".jar") || child.getName().endsWith(".zip"))) {
          final String url = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, child.getPath()) + JarFileSystem.JAR_SEPARATOR;
          final VirtualFile jarVirtualFile = VirtualFileManager.getInstance().findFileByUrl(url);
          if (jarVirtualFile != null) {
            jars.add(jarVirtualFile);
          }
        }
      }
      if (jars.size() > 0) {
        final LibraryTable libraryTable = rootModel.getModuleLibraryTable();
        final Library library = libraryTable.createLibrary("projectlib");
        final Library.ModifiableModel libraryModifiableModel = library.getModifiableModel();
        for (final VirtualFile jar : jars) {
          libraryModifiableModel.addRoot(jar, OrderRootType.CLASSES);
        }
        libraryModifiableModel.commit();
      }
    }
    // configure source and output path
    final ContentEntry contentEntry = rootModel.addContentEntry(myModuleRoot);
    contentEntry.addSourceFolder(mySourceDir, false);
    rootModel.setCompilerOutputPath(myClassesDir);
    rootModel.setExcludeOutput(true);

    // Mock JDK is used by default. Uncomment in order to use 'real' JDK if needed
    //ProjectJdkEx jdk = ProjectJdkTable.getInstance().getInternalJdk();
    //rootManager.setJdk(jdk);
    //final ProjectJdk jdk = rootModel.getJdk();
    rootModel.commit();
  }

  protected VirtualFile getDataRootDir(final String testName) {
    final String testDataPath = myDataRootPath.replace(File.separatorChar, '/') + "/" + testName;
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(testDataPath);
  }

  protected String getSourceDirRelativePath() {
    return ".";
  }

  protected void copyFiles(VirtualFile dataDir, VirtualFile destDir, VirtualFileFilter filter) throws Exception {
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
    for (VirtualFile child : children) {
      if (!filter.accept(child)) continue;
      if (child.isDirectory()) { // is a directory
        VirtualFile destChild = destDir.findChild(child.getName());
        if (destChild == null) {
          destChild = destDir.createChildDirectory(this, child.getName());
        }
        copyFiles(child, destChild, filter);
      }
      else {
        String name = child.getName();
        long currentTimeStamp = -1;
        if (name.endsWith(".new")) {
          name = name.substring(0, name.length() - ".new".length());
          VirtualFile destChild = destDir.findChild(name);
          if (destChild != null && destChild.isValid()) {
//            System.out.println("Replacing " + destChild.getPath() + " with " + name + "; current timestamp is " + destChild.getPhysicalTimeStamp());
            currentTimeStamp = destChild.getActualTimeStamp();
            final String destChildPath = destChild.getPath().replace('/', File.separatorChar);
            destChild.delete(this);
            assertTrue("File " + destChildPath + " should be deleted in order for the test to work properly!",
                       !new File(destChildPath).exists());
          }
        }

//        if (!"dsl".equals(System.getProperty("user.name"))){
//          assertTrue("dsl was stupid enough to checkin these changes", false);
//        } else {
//          Thread.sleep(2000);
//        }

//        System.out.println("time before copying= " + System.currentTimeMillis());
        VirtualFile newChild = VfsUtil.copyFile(this, child, destDir, name);
        assertTrue("Timestamps of test data files must differ in order for the test to work properly!\n " + child.getPath(),
                   currentTimeStamp != newChild.getActualTimeStamp());
//        System.out.println("time after copying= " + System.currentTimeMillis());
//        System.out.println("{TestCase:} copied " + child.getPath() + "origin timestamp is " + child.getPhysicalTimeStamp() + "; new timestamp is "+newChild.getPhysicalTimeStamp());
      }
    }
  }

  private boolean shouldDelete(VirtualFile file) {
    String path = VfsUtil.getRelativePath(file, myModuleRoot, '/');
    return myData != null && myData.shouldDeletePath(path);
  }

  private static class JavaFilesFilter implements VirtualFileFilter {
    public boolean accept(VirtualFile file) {
      return file.isDirectory() || file.getName().endsWith(".java");
    }
  }

  private static class NewFilesFilter implements VirtualFileFilter {
    public boolean accept(VirtualFile file) {
      return file.isDirectory() || file.getName().endsWith(".new");
    }
  }

  protected void runBareRunnable(Runnable runnable) throws Throwable, InvocationTargetException {
    runnable.run();
  }

  protected void tearDown() throws Exception {
    final Exception[] exceptions = new Exception[] {null};
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        try {
          myDeletedPaths.clear();
          myRecompiledPaths.clear();
          myData = null;
          myClassesDir = null;
          myDataDir = null;
          mySourceDir = null;
          CompilerTestCase.super.tearDown();
        } catch (Exception e) {
          exceptions[0] = e;
        }
      }
    }, ModalityState.NON_MMODAL);
    if (exceptions[0] != null) {
      throw exceptions[0];
    }
  }
}
