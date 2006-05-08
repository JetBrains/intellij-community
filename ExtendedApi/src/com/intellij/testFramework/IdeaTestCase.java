package com.intellij.testFramework;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.idea.IdeaLogger;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InvocationEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author mike
 */
@NonNls public abstract class IdeaTestCase extends TestCase implements DataProvider {
  protected static final String PROFILE = "Configurable";
  static {
    Logger.setFactory(TestLoggerFactory.getInstance());
  }

  protected static IdeaTestApplication ourApplication;

  protected boolean myRunCommandForTest = false;

  protected ProjectManagerEx myProjectManager;
  protected Project myProject;
  protected Module myModule;
  protected static Collection<File> myFilesToDelete;
  protected boolean myAssertionsInTestDetected;
  protected static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.IdeaTestCase");
  public static Thread ourTestThread;
  private static TestCase ourTestCase = null;
  public static final long DEFAULT_TEST_TIME = 300L;
  public static long ourTestTime = DEFAULT_TEST_TIME;
  private static final MyThreadGroup MY_THREAD_GROUP = new MyThreadGroup();
  protected static long getTimeRequired() {
    return DEFAULT_TEST_TIME;
  }

  protected String getTestName(boolean lowercaseFirstLetter) {
    String name = getName();
    assertTrue(name.startsWith("test"));
    name = name.substring("test".length());
    if (lowercaseFirstLetter) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  protected void initApplication() throws Exception {
    ourApplication = IdeaTestApplication.getInstance();
    ourApplication.setDataProvider(this);
  }

  protected void setUp() throws Exception {
    super.setUp();
    if (ourTestCase != null) {
      String message = "Previous test " + ourTestCase +
                       " haven't called tearDown(). Probably overriden without super call.";
      ourTestCase = null;
      assertTrue(message, false);
    }
    ourTestCase = this;
    IdeaLogger.ourErrorsOccurred = null;

    LOG.info(getClass().getName() + ".setUp()");

    initApplication();

    myFilesToDelete = new HashSet<File>();

    setUpProject();
  }

  public Project getProject() {
    return myProject;
  }

  public final PsiManager getPsiManager() {
    return PsiManager.getInstance(myProject);
  }

  public Module getModule() {
    return myModule;
  }

  protected void setUpProject() throws IOException {
    myProjectManager = ProjectManagerEx.getInstanceEx();
    LOG.assertTrue(myProjectManager != null, "Cannot instaitiate ProjectManager component");

    File projectFile = File.createTempFile("temp", ".ipr");
    myFilesToDelete.add(projectFile);

    myProject = myProjectManager.newProject(projectFile.getPath(), false, false);

    setUpModule();

    setUpJdk();

    ((StartupManagerImpl)StartupManager.getInstance(myProject)).runStartupActivities();
  }

  protected void setUpModule() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myModule = createMainModule();
      }
    });
  }

  protected Module createMainModule() {
    return createModule(myProject.getName());
  }

  protected Module createModule(final String moduleName) {
    final VirtualFile projectFile = myProject.getProjectFile();
    assertNotNull(projectFile);
    final File moduleFile = new File(projectFile.getParent().getPath().replace('/', File.separatorChar),
                                     moduleName + ".iml");
    try {
      moduleFile.createNewFile();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    myFilesToDelete.add(moduleFile);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
    Module module = ModuleManager.getInstance(myProject).newModule(virtualFile.getPath());
    module.getModuleFile();
    return module;
  }

  private void cleanupApplicationCaches() {
    try {
      LocalFileSystemImpl localFileSystem = (LocalFileSystemImpl)LocalFileSystem.getInstance();
      if (localFileSystem != null) {
        localFileSystem.cleanupForNextTest();
      }
    }
    catch (IOException e) {
      // ignore
    }
    VirtualFilePointerManagerImpl virtualFilePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
    if (virtualFilePointerManager != null) {
      virtualFilePointerManager.cleanupForNextTest();
    }
    resetAllFields();
  }

  private static void doPostponedFormatting(final Project project) {
    try {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
            }
          });
        }
      });
    }
    catch (Throwable e) {
      // Way to go...
    }
  }

  protected void tearDown() throws Exception {
    InspectionProfileManager.getInstance().deleteProfile(PROFILE);
    try {
      assertNotNull("Application components damaged", ProjectManager.getInstance());

      ApplicationManager.getApplication().runWriteAction(EmptyRunnable.getInstance()); // Flash posponed formatting if any.
      FileDocumentManager.getInstance().saveAllDocuments();

      doPostponedFormatting(myProject);

      try {
        Disposer.dispose(myProject);

        for (final File fileToDelete : myFilesToDelete) {
          delete(fileToDelete);
        }

        Throwable fromThreadGroup = MY_THREAD_GROUP.popThrowable();
        if (fromThreadGroup != null) {
          throw new RuntimeException(fromThreadGroup);
        }

        if (!myAssertionsInTestDetected) {
          if (IdeaLogger.ourErrorsOccurred != null) {
            throw IdeaLogger.ourErrorsOccurred;
          }
          assertTrue("Logger errors occurred in " + getFullName(), IdeaLogger.ourErrorsOccurred == null);
        }

        ourApplication.setDataProvider(null);

      }
      finally {
        ourTestCase = null;
      }
      super.tearDown();

      EditorFactory editorFactory = EditorFactory.getInstance();
      final Editor[] allEditors = editorFactory.getAllEditors();
      ((EditorFactoryImpl)editorFactory).validateEditorsAreReleased(getProject());
      for (Editor editor : allEditors) {
        editorFactory.releaseEditor(editor);
      }
      assertEquals(0, allEditors.length);
    }
    finally {
      myProjectManager = null;
      myProject = null;
      myModule = null;
      myFilesToDelete = null;
    }


  }

  protected void resetAllFields() {
    resetClassFields(getClass());
  }

  private void resetClassFields(final Class<?> aClass) {
    if (aClass == null) return;

    final Field[] fields = aClass.getDeclaredFields();
    for (Field field : fields) {
      final int modifiers = field.getModifiers();
      if ((modifiers & Modifier.FINAL) == 0
          &&  (modifiers & Modifier.STATIC) == 0
          && !field.getType().isPrimitive()) {
        field.setAccessible(true);
        try {
          field.set(this, null);
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }

    if (aClass == IdeaTestCase.class) return;
    resetClassFields(aClass.getSuperclass());
  }

  private String getFullName() {
    return getClass().getName() + "." + getName();
  }

  private void delete(File file) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      for (File fileToDelete : files) {
        delete(fileToDelete);
      }
    }

    boolean b = file.delete();
    if (!b && file.exists() && !myAssertionsInTestDetected) {
      assertTrue("Can't delete " + file.getAbsolutePath() + " in " + getFullName(), false);
    }
  }

  protected void setUpJdk() {
    //final ProjectJdkEx jdk = ProjectJdkUtil.getDefaultJdk("java 1.4");
    final ProjectJdk jdk = getTestProjectJdk();
//    ProjectJdkImpl jdk = ProjectJdkTable.getInstance().addJdk(defaultJdk);
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final ModifiableRootModel rootModel = rootManager.getModifiableModel();
          rootModel.setJdk(jdk);
          rootModel.commit();
        }
      });
    }
  }

  protected ProjectJdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk("java 1.4");
  }

  public void runBare() throws Throwable {
    final Throwable[] throwable = new Throwable[1];

    Thread thread = new Thread(MY_THREAD_GROUP, new Runnable() {
      public void run() {
        try {
          runBareImpl();
        }
        catch (Throwable th) {
          throwable[0] = th;
        } finally {
          try {
            SwingUtilities.invokeAndWait(new Runnable() {
              public void run() {
                cleanupApplicationCaches();
              }
            });
          }
          catch (Throwable e) {
            // Ignore
          }
        }
      }
    }, "IDEA Test Case Thread");
    thread.start();
    thread.join();

    if (throwable[0] != null) {
      throw throwable[0];
    }
  }

  private void runBareImpl() throws Throwable {
    final Throwable[] throwables = new Throwable[1];
    Runnable runnable = new Runnable() {
      public void run() {
        ourTestThread = Thread.currentThread();
        ourTestTime = getTimeRequired();
        try {
          setUp();
          try {
            myAssertionsInTestDetected = true;
            runTest();
            myAssertionsInTestDetected = false;
          }
          finally {
            try {
              tearDown();
            } catch(Throwable th) { th.printStackTrace(); }
          }
        }
        catch (Throwable throwable) {
          throwables[0] = throwable;
        }
        finally {
          ourTestThread = null;
        }
      }
    };

    runBareRunnable(runnable);

    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }

    if (throwables[0] != null) {
      throw throwables[0];
    }

    // just to make sure all deffered Runnable's to finish
    waitForAllLaters();
    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }
  }

  private static void waitForAllLaters() throws InterruptedException, InvocationTargetException {
    for (int i = 0; i < 3; i++) {
      SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());
    }
  }

  protected void runBareRunnable(Runnable runnable) throws Throwable, InvocationTargetException {
    SwingUtilities.invokeAndWait(runnable);
  }

  protected void runTest() throws Throwable {
    /*
    Method runMethod = null;
    try {
      runMethod = getClass().getMethod(getName(), new Class[0]);
    }
    catch (NoSuchMethodException e) {
      fail("Method \"" + getName() + "\" not found");
    }
    if (runMethod != null && !Modifier.isPublic(runMethod.getModifiers())) {
      fail("Method \"" + getName() + "\" should be public");
    }

    final Method method = runMethod;
    */

    final Throwable[] throwables = new Throwable[1];

    Runnable runnable = new Runnable() {
      public void run() {
        try {
          IdeaTestCase.super.runTest();
          /*
          method.invoke(IdeaTestCase.this, new Class[0]);
          */
        }
        catch (InvocationTargetException e) {
          e.fillInStackTrace();
          throwables[0] = e.getTargetException();
        }
        catch (IllegalAccessException e) {
          e.fillInStackTrace();
          throwables[0] = e;
        }
        catch (Throwable e) {
          throwables[0] = e;
        }
      }
    };

    invokeTestRunnable(runnable);

    if (throwables[0] != null) {
      throw throwables[0];
    }
  }

  protected void invokeTestRunnable(final Runnable runnable) throws Exception {
    final Exception[] e = new Exception[1];
    Runnable runnable1 = new Runnable() {
      public void run() {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          try {
            ApplicationManager.getApplication().runWriteAction(runnable);
          }
          catch (Exception e1) {
            e[0] = e1;
          }
        }
        else {
          runnable.run();
        }
      }
    };

    if (myRunCommandForTest) {
      CommandProcessor.getInstance().executeCommand(myProject, runnable1, "", null);
    }
    else {
      runnable1.run();
    }

    if (e[0] != null) {
      throw e[0];
    }
  }

  public Object getData(String dataId) {
    if (dataId.equals(DataConstants.PROJECT)) {
      return myProject;
    }
    else if (dataId.equals(DataConstants.EDITOR)) {
      return FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    }
    else {
      return null;
    }
  }

  public static void dispatchAllInvocationEvents() {
    final EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    while (true) {
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) break;
      try {
        AWTEvent event1 = eventQueue.getNextEvent();
        if (event1 instanceof InvocationEvent) {
          ((InvocationEvent)event1).dispatch();
        }
      }
      catch (Exception e) {
        LOG.error(e); //?
      }
    }
  }

  public static File createTempDir(final String prefix) throws IOException {
    final File tempDirectory = FileUtil.createTempDirectory(prefix, null);
    myFilesToDelete.add(tempDirectory);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        VirtualFileManager.getInstance().refresh(false);
      }
    });

    return tempDirectory;
  }

  protected static VirtualFile getVirtualFile(final File file) {
    VirtualFile virtualFile;
    try {
      virtualFile = LocalFileSystem.getInstance().findFileByPath(file.getCanonicalPath().replace(File.separatorChar, '/'));
    }
    catch (IOException e) {
      assertTrue(false);
      virtualFile = null;
    }
    return virtualFile;
  }

  private static class MyThreadGroup extends ThreadGroup {
    private Throwable myThrowable;
    @NonNls private static final String IDEATEST_THREAD_GROUP = "IDEATest";

    public MyThreadGroup() {
      super(IDEATEST_THREAD_GROUP);
    }

    public void uncaughtException(Thread t, Throwable e) {
      myThrowable = e;
      super.uncaughtException(t, e);
    }

    public Throwable popThrowable() {
      try {
        return myThrowable;
      }
      finally {
        myThrowable = null;
      }
    }
  }
}
