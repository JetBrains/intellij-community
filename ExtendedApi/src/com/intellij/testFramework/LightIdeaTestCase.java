package com.intellij.testFramework;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.idea.IdeaLogger;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * A testcase that provides IDEA application and project. Note both are reused for each test run in the session so
 * be careful to return all the modification made to application and project components (such as settings) after
 * test is finished so other test aren't affected. The project is initialized with single module that have single
 * content&amp;source entry. For your convinience the project may be equipped with some mock JDK so your tests may
 * refer to external classes. In order to enable this feature you have to have a folder named "mockJDK" under
 * idea installation home that is used for test running. Place src.zip under that folder. We'd suggest this is real mock
 * so it contains classes that is really needed in order to speed up tests startup.
 */
@NonNls public class LightIdeaTestCase extends TestCase implements DataProvider {
  private static IdeaTestApplication ourApplication;
  private static Project ourProject;
  private static Module ourModule;
  private static ProjectJdk ourJDK;
  private static PsiManager ourPsiManager;
  private boolean myAssertionsInTestDetected;
  private static VirtualFile ourSourceRoot;
  private static TestCase ourTestCase = null;
  public static Thread ourTestThread;

  private Map<String, LocalInspectionTool> myAvailableTools = new HashMap<String, LocalInspectionTool>();

  /**
   * @return Project to be used in tests for example for project components retrieval.
   */
  public static Project getProject() {
    return ourProject;
  }

  /**
   * @return Module to be used in tests for example for module components retrieval.
   */
  protected static Module getModule() {
    return ourModule;
  }

  /**
   * Shortcut to PsiManager.getInstance(getProject())
   */
  protected static PsiManager getPsiManager() {
    if (ourPsiManager == null) {
      ourPsiManager = PsiManager.getInstance(ourProject);
    }
    return ourPsiManager;
  }

  private void initApplication() throws Exception {
    ourApplication = IdeaTestApplication.getInstance();
    ourApplication.setDataProvider(this);
    cleanupApplicationCaches();
  }

  private static void cleanupApplicationCaches() {
    ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).cleanupForNextTest();
    if (ourProject != null) {
      UndoManager.getInstance(ourProject).dropHistory();
    }

    /*
    if (ourPsiManager != null) {
      ((PsiManagerImpl)ourPsiManager).cleanupForNextTest();
    }
    */
  }

  private void initProject() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        if (ourProject != null) {
          ProjectUtil.closeProject(ourProject);
        }
        ourProject = ProjectManagerEx.getInstanceEx().newProject("", false, false);
        ourPsiManager = null;
        ourModule = createMainModule();

        ourSourceRoot = DummyFileSystem.getInstance().createRoot("src");

        final ModuleRootManager rootManager = ModuleRootManager.getInstance(ourModule);

        final ModifiableRootModel rootModel = rootManager.getModifiableModel();

        ProjectJdk projectJDK = getProjectJDK();
        if (projectJDK != null) {
          ourJDK = projectJDK;
          rootModel.setJdk(projectJDK);
        }

        final ContentEntry contentEntry = rootModel.addContentEntry(ourSourceRoot);
        contentEntry.addSourceFolder(ourSourceRoot, false);

        rootModel.commit();

        ProjectRootManager.getInstance(ourProject).addModuleRootListener(new ModuleRootListener() {
          public void beforeRootsChange(ModuleRootEvent event) {
            if (!event.isCausedByFileTypesChange()) {
              fail("Root modification in LightIdeaTestCase is not allowed.");
            }
          }

          public void rootsChanged(ModuleRootEvent event) {

          }
        });

        ModuleManager.getInstance(ourProject).addModuleListener(new ModuleListener() {
          public void moduleAdded(Project project, Module module) {
            fail("Adding modules is not permitted in LightIdeaTestCase.");
          }

          public void beforeModuleRemoved(Project project, Module module) {
          }

          public void moduleRemoved(Project project, Module module) {

          }

          public void modulesRenamed(Project project, List<Module> modules) {
          }

        });

        //((PsiManagerImpl) PsiManager.getInstance(ourProject)).runStartupActivity();
        ((StartupManagerImpl)StartupManager.getInstance(getProject())).runStartupActivities();
      }
    });
  }

  protected Module createMainModule() {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      public Module compute() {
        return ModuleManager.getInstance(ourProject).newModule("");
      }
    });
  }

  /**
   * @return The only source root
   */
  protected static VirtualFile getSourceRoot() {
    return ourSourceRoot;
  }

  protected void setUp() throws Exception {
    super.setUp();
    assertNull("Previous test " + ourTestCase + " haven't called tearDown(). Probably overriden without super call.",
               ourTestCase);
    IdeaLogger.ourErrorsOccurred = null;

    initApplication();

    if (ourProject == null || isJDKChanged()) {
      initProject();
    }

    final LocalInspectionTool[] tools = configureLocalInspectionTools();
    for (LocalInspectionTool tool : tools) {
      enableInspectionTool(tool);
    }

    final InspectionProfileImpl profile = new InspectionProfileImpl("Configurable") {
      public LocalInspectionTool[] getHighlightingLocalInspectionTools() {
        final Collection<LocalInspectionTool> tools = myAvailableTools.values();
        return tools.toArray(new LocalInspectionTool[tools.size()]);
      }

      public boolean isToolEnabled(HighlightDisplayKey key) {
        if (key == null) return false;
        return myAvailableTools.containsKey(key.toString()) || isNonInspectionHighlighting(key);
      }

      public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey key) {
        final LocalInspectionTool localInspectionTool = myAvailableTools.get(key.toString());
        return localInspectionTool != null ? localInspectionTool.getDefaultLevel() : HighlightDisplayLevel.WARNING;
      }


      public InspectionTool getInspectionTool(String shortName) {
        if (myAvailableTools.containsKey(shortName)) {
          return new LocalInspectionToolWrapper(myAvailableTools.get(shortName));
        }
        return null;
      }

    };
    final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
    inspectionProfileManager.addProfile(profile);
    inspectionProfileManager.setRootProfile(profile.getName());

    assertFalse(getPsiManager().isDisposed());

    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(new CodeStyleSettings());

  }

  protected Set<String> getAvailableInspections(){
    return myAvailableTools.keySet();
  }

  protected void enableInspectionTool(LocalInspectionTool tool){
    final String shortName = tool.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null){
      HighlightDisplayKey.register(shortName, tool.getDisplayName(), tool.getID());
    }
    myAvailableTools.put(shortName, tool);
  }

  protected void disableInspectionTool(String shortName){
    myAvailableTools.remove(shortName);
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[0];
  }

  protected void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(null);
    assertNotNull("Application components damaged", ProjectManager.getInstance());
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          final VirtualFile[] children = ourSourceRoot.getChildren();
          for (VirtualFile aChildren : children) {
            aChildren.delete(this);
          }
        }
        catch (IOException e) {
          //noinspection CallToPrintStackTrace
          e.printStackTrace();
        }
      }
    });
//    final Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
//    assertTrue(Arrays.asList(openProjects).contains(ourProject));
    assertFalse(PsiManager.getInstance(getProject()).isDisposed());
    if (!myAssertionsInTestDetected) {
      if (IdeaLogger.ourErrorsOccurred != null) {
        throw IdeaLogger.ourErrorsOccurred;
      }
      //assertTrue("Logger errors occurred. ", IdeaLogger.ourErrorsOccurred == null);
    }

    ourApplication.setDataProvider(null);
    ourTestCase = null;
    ((PsiManagerImpl)ourPsiManager).cleanupForNextTest();
    super.tearDown();

    final Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    if (allEditors.length > 0) {
      for (Editor allEditor : allEditors) {
        EditorFactory.getInstance().releaseEditor(allEditor);
      }
      fail("Unreleased editors: " + allEditors.length);
    }
  }

  public final void runBare() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        try {
          ourTestThread = Thread.currentThread();
          startRunAndTear();
        }
        catch (Throwable throwable) {
          throwables[0] = throwable;
        }
        finally {
          ourTestThread = null;
        }
      }
    });

    if (throwables[0] != null) {
      throw throwables[0];
    }

    // just to make sure all deffered Runnable's to finish
    SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());

    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }
  }

  private void startRunAndTear() throws Throwable {
    setUp();
    try {
      myAssertionsInTestDetected = true;
      runTest();
      myAssertionsInTestDetected = false;
    }
    finally {
      try{
        tearDown();
      }
      catch(Throwable th){
        //noinspection CallToPrintStackTrace
        th.printStackTrace();
      }
    }
  }

  public Object getData(String dataId) {
    if (dataId.equals(DataConstants.PROJECT)) {
      return ourProject;
    }
    else {
      return null;
    }
  }

  private boolean isJDKChanged() {
    ProjectJdk jdk = getProjectJDK();
    return ourJDK == null || !Comparing.equal(ourJDK.getVersionString(), jdk.getVersionString());
  }

  protected ProjectJdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk("java 1.4");
  }

  /**
   * Creates dummy source file. One is not placed under source root so some PSI functions like resolve to external classes
   * may not work. Though it works significantly faster and yet can be used if you need to create some PSI structures for
   * test purposes
   *
   * @param fileName - name of the file to create. Extension is used to choose what PSI should be created like java, jsp, aj, xml etc.
   * @param text     - file text.
   * @return dummy psi file.
   * @throws IncorrectOperationException
   */
  protected PsiFile createFile(String fileName, String text) throws IncorrectOperationException {
    return getPsiManager().getElementFactory().createFileFromText(fileName, text);
  }

  /**
   * Convinient conversion of testSomeTest -> someTest | SomeTest where testSomeTest is the name of current test.
   *
   * @param lowercaseFirstLetter - whether first letter after test should be lowercased.
   */
  protected String getTestName(boolean lowercaseFirstLetter) {
    String name = getName();
    assertTrue("Test name should start with 'test'", name.startsWith("test"));
    name = name.substring("test".length());
    if (lowercaseFirstLetter) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  protected void commitDocument(final Document document) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
  }

  protected Document getDocument(final XmlFile file) {
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }
}
