package com.intellij.roots;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.util.messages.MessageBusConnection;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author dsl
 */
public class MultiModuleEditingTest extends ModuleTestCase {

  private static final String TEST_PATH = PathManagerEx.getTestDataPath() +
                                          "/moduleRootManager/multiModuleEditing".replace('/', File.separatorChar);
  protected void setUpModule() {
  }

  protected void setUpJdk() {
  }

  public void testAddTwoModules() throws Exception {
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    final MyModuleListener moduleListener = new MyModuleListener();
    connection.subscribe(ProjectTopics.MODULES, moduleListener);
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);

    final Module moduleA;
    final Module moduleB;

    {
      final ModifiableModuleModel modifiableModel = moduleManager.getModifiableModel();
      moduleA = modifiableModel.newModule("a.iml", StdModuleTypes.JAVA);
      moduleB = modifiableModel.newModule("b.iml", StdModuleTypes.JAVA);
      assertEquals("Changes are not applied until commit", 0, moduleManager.getModules().length);
      //noinspection SSBasedInspection
      moduleListener.assertCorrectEvents(new String[0][]);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          modifiableModel.commit();
        }
      });
    }

    assertEquals(2, moduleManager.getModules().length);
    assertTrue(moduleManager.findModuleByName("a").equals(moduleA));
    assertTrue(moduleManager.findModuleByName("b").equals(moduleB));
    moduleListener.assertCorrectEvents(new String[][]{{"+a", "+b"}});

    {
      final ModifiableModuleModel modifiableModel = moduleManager.getModifiableModel();
      modifiableModel.disposeModule(moduleA);
      modifiableModel.disposeModule(moduleB);
      assertEquals("Changes are not applied until commit", 2, moduleManager.getModules().length);
      moduleListener.assertCorrectEvents(new String[][]{{"+a", "+b"}});
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          modifiableModel.commit();
        }
      });
    }

    assertEquals(0, moduleManager.getModules().length);
    moduleListener.assertCorrectEvents(new String[][]{{"+a", "+b"}, {"-a", "-b"}});
    connection.disconnect();
  }

  public void testRootsEditing() {
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final MyModuleListener moduleListener = new MyModuleListener();
    connection.subscribe(ProjectTopics.MODULES, moduleListener);

    final Module moduleA;
    final Module moduleB;
    {
      final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      moduleA = moduleModel.newModule("a.iml", StdModuleTypes.JAVA);
      moduleB = moduleModel.newModule("b.iml", StdModuleTypes.JAVA);
      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelB.addModuleOrderEntry(moduleA);

      final ContentEntry contentEntryA = rootModelA.addContentEntry(getVirtualFileInTestData("a"));
      contentEntryA.addSourceFolder(getVirtualFileInTestData("a/src"), false);
      final ContentEntry contentEntryB = rootModelB.addContentEntry(getVirtualFileInTestData("b"));
      contentEntryB.addSourceFolder(getVirtualFileInTestData("b/src"), false);

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          ProjectRootManagerEx.getInstanceEx(myProject).multiCommit(moduleModel, new ModifiableRootModel[]{rootModelB, rootModelA});
        }
      });
    }

    final JavaPsiFacade psiManager = getJavaFacade();
    assertNull(psiManager.findClass("j.B", GlobalSearchScope.moduleWithDependenciesScope(moduleA)));
    assertNull(psiManager.findClass("q.A", GlobalSearchScope.moduleScope(moduleB)));

    assertNotNull(psiManager.findClass("q.A", GlobalSearchScope.moduleScope(moduleA)));
    assertNotNull(psiManager.findClass("q.A", GlobalSearchScope.moduleWithDependenciesScope(moduleB)));
    assertNotNull(psiManager.findClass("j.B", GlobalSearchScope.moduleScope(moduleB)));
    assertNotNull(psiManager.findClass("j.B", GlobalSearchScope.moduleWithDependenciesScope(moduleB)));

    moduleManager.disposeModule(moduleB);
    moduleManager.disposeModule(moduleA);
    moduleListener.assertCorrectEvents(new String[][]{{"+b", "+a"}, {"-b"}, {"-a"}});

    connection.disconnect();
  }

  public void testRenaming() throws Exception{
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final Module moduleA;
    final Module moduleB;

    {
      final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      moduleA = moduleModel.newModule("a.iml", StdModuleTypes.JAVA);
      moduleB = moduleModel.newModule("b.iml", StdModuleTypes.JAVA);
      final Module moduleC = moduleModel.newModule("c.iml", StdModuleTypes.JAVA);
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelB.addModuleOrderEntry(moduleC);
      moduleModel.disposeModule(moduleC);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          ProjectRootManager.getInstance(myProject).multiCommit(moduleModel, new ModifiableRootModel[]{rootModelB});
        }
      });
    }

    final ModuleRootManager rootManagerB = ModuleRootManager.getInstance(moduleB);
    assertEquals(0, rootManagerB.getDependencies().length);
    final String[] dependencyModuleNames = rootManagerB.getDependencyModuleNames();
    assertEquals(1, dependencyModuleNames.length);
    assertEquals("c", dependencyModuleNames[0]);

    {
      final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      moduleModel.renameModule(moduleA, "c");
      moduleModel.commit();
    }

    assertEquals(1, rootManagerB.getDependencies().length);
    assertEquals(moduleA, rootManagerB.getDependencies()[0]);
    assertEquals("c", moduleA.getName());
    moduleManager.disposeModule(moduleA);
    moduleManager.disposeModule(moduleB);
  }

  private VirtualFile getVirtualFileInTestData(final String relativeVfsPath) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        final String path = TEST_PATH + File.separatorChar + getTestName(true) + File.separatorChar + relativeVfsPath.replace('/', File.separatorChar);
        final VirtualFile result = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
        assertNotNull("File " + path + " doen\'t exist", result);
        return result;
      }
    });
  }


  private class MyModuleListener implements ModuleListener {
    private final List<String> myLog = new ArrayList<String>();

    public void moduleRemoved(Project project, Module module) {
      myLog.add("-" + module.getName());
    }

    public void modulesRenamed(Project project, List<Module> modules) {
      // write something
    }

    public void moduleAdded(Project project, Module module) {
      myLog.add("+" + module.getName());
    }

    public void beforeModuleRemoved(Project project, Module module) {
    }

    public void assertCorrectEvents(String[][] expected) {
      int runningIndex = 0;
      for (int chunkIndex = 0; chunkIndex < expected.length; chunkIndex++) {
        String[] chunk = expected[chunkIndex];
        final List<String> expectedChunkList = new ArrayList<String>(Arrays.asList(chunk));
        int nextIndex = runningIndex + chunk.length;
        assertTrue("Expected chunk " + expectedChunkList.toString(), nextIndex <= myLog.size());
        final List<String> actualChunkList = new ArrayList<String>(myLog.subList(runningIndex, nextIndex));
        Collections.sort(expectedChunkList);
        Collections.sort(actualChunkList);
        assertEquals("Chunk " + chunkIndex, expectedChunkList.toString(), actualChunkList.toString());
        runningIndex = nextIndex;
      }
      assertEquals("More events than needed", runningIndex, myLog.size());
    }
  }
}
