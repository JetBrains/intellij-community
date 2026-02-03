// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MultiModuleEditingTest extends JavaModuleTestCase {
  private static final String TEST_PATH = PathManagerEx.getTestDataPath() +
                                          "/moduleRootManager/multiModuleEditing".replace('/', File.separatorChar);
  @Override
  protected void setUpModule() {
  }

  @Override
  protected void setUpJdk() {
  }

  public void testAddTwoModules() {
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    final MyModuleListener moduleListener = new MyModuleListener();
    connection.subscribe(ModuleListener.TOPIC, moduleListener);
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);

    final Module moduleA;
    final Module moduleB;

    Path dir = ProjectKt.getStateStore(myProject).getProjectBasePath();

    {
      final ModifiableModuleModel modifiableModel = moduleManager.getModifiableModel();
      moduleA = modifiableModel.newModule(dir.resolve("a.iml"), JavaModuleType.getModuleType().getId());
      moduleB = modifiableModel.newModule(dir.resolve("b.iml"), JavaModuleType.getModuleType().getId());
      assertEquals("Changes are not applied until commit", 0, moduleManager.getModules().length);
      //noinspection SSBasedInspection
      moduleListener.assertCorrectEvents(new String[0][]);
      ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
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
      ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    }

    assertEquals(0, moduleManager.getModules().length);
    moduleListener.assertCorrectEvents(new String[][]{{"+a", "+b"}, {"-a", "-b"}});
    connection.disconnect();
  }

  public void testRootsEditing() {
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final MyModuleListener moduleListener = new MyModuleListener();
    connection.subscribe(ModuleListener.TOPIC, moduleListener);

    Path dir = ProjectKt.getStateStore(myProject).getProjectBasePath();

    final Module moduleA;
    final Module moduleB;
    {
      final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      moduleA = moduleModel.newModule(dir.resolve("a.iml"), JavaModuleType.getModuleType().getId());
      moduleB = moduleModel.newModule(dir.resolve("b.iml"), JavaModuleType.getModuleType().getId());
      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelB.addModuleOrderEntry(moduleA);

      final ContentEntry contentEntryA = rootModelA.addContentEntry(getVirtualFileInTestData("a"));
      contentEntryA.addSourceFolder(getVirtualFileInTestData("a/src"), false);
      final ContentEntry contentEntryB = rootModelB.addContentEntry(getVirtualFileInTestData("b"));
      contentEntryB.addSourceFolder(getVirtualFileInTestData("b/src"), false);

      ApplicationManager.getApplication().runWriteAction(() -> ModifiableModelCommitter.multiCommit(new ModifiableRootModel[]{rootModelB, rootModelA}, moduleModel));
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
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

    Path dir = ProjectKt.getStateStore(myProject).getProjectBasePath();

    {
      final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      moduleA = moduleModel.newModule(dir.resolve("a.iml"), JavaModuleType.getModuleType().getId());
      moduleB = moduleModel.newModule(dir.resolve("b.iml"), JavaModuleType.getModuleType().getId());
      final Module moduleC = moduleModel.newModule(dir.resolve("c.iml"), JavaModuleType.getModuleType().getId());
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelB.addModuleOrderEntry(moduleC);
      moduleModel.disposeModule(moduleC);
      ApplicationManager.getApplication().runWriteAction(() -> ModifiableModelCommitter.multiCommit(new ModifiableRootModel[]{rootModelB}, moduleModel));
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    }

    final ModuleRootManager rootManagerB = ModuleRootManager.getInstance(moduleB);
    assertEquals(0, rootManagerB.getDependencies().length);
    final String[] dependencyModuleNames = rootManagerB.getDependencyModuleNames();
    assertEquals(1, dependencyModuleNames.length);
    assertEquals("c", dependencyModuleNames[0]);

    {
      final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      moduleModel.renameModule(moduleA, "c");
      assertSame(moduleA, moduleModel.findModuleByName("a"));
      assertSame(moduleA, moduleManager.findModuleByName("a"));
      assertEquals("c", moduleModel.getNewName(moduleA));
      assertEquals("b", moduleModel.getActualName(moduleB));
      assertEquals("c", moduleModel.getActualName(moduleA));
      assertSame(moduleA, moduleModel.getModuleToBeRenamed("c"));
      ApplicationManager.getApplication().runWriteAction(() -> moduleModel.commit());
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    }

    assertEquals(1, rootManagerB.getDependencies().length);
    assertEquals(moduleA, rootManagerB.getDependencies()[0]);
    assertSame(moduleA, moduleManager.findModuleByName("c"));
    assertNull(moduleManager.findModuleByName("a"));
    assertEquals("c", moduleA.getName());
    moduleManager.disposeModule(moduleA);
    moduleManager.disposeModule(moduleB);
  }

  private VirtualFile getVirtualFileInTestData(final String relativeVfsPath) {
    return WriteCommandAction.runWriteCommandAction(null, (Computable<VirtualFile>)() -> {
      final String path =
        TEST_PATH + File.separatorChar + getTestName(true) + File.separatorChar + relativeVfsPath.replace('/', File.separatorChar);
      final VirtualFile result = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
      assertNotNull("File " + path + " doesn't exist", result);
      return result;
    });
  }


  private static class MyModuleListener implements ModuleListener {
    private final List<String> myLog = new ArrayList<>();

    @Override
    public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
      myLog.add("-" + module.getName());
    }

    @Override
    public void modulesAdded(@NotNull Project project, @NotNull List<? extends Module> modules) {
      for (Module module : modules) {
        myLog.add("+" + module.getName());
      }
    }
    public void assertCorrectEvents(String[][] expected) {
      int runningIndex = 0;
      for (int chunkIndex = 0; chunkIndex < expected.length; chunkIndex++) {
        String[] chunk = expected[chunkIndex];
        final List<String> expectedChunkList = new ArrayList<>(Arrays.asList(chunk));
        int nextIndex = runningIndex + chunk.length;
        assertTrue("Expected chunk " + expectedChunkList.toString(), nextIndex <= myLog.size());
        final List<String> actualChunkList = new ArrayList<>(myLog.subList(runningIndex, nextIndex));
        Collections.sort(expectedChunkList);
        Collections.sort(actualChunkList);
        assertEquals("Chunk " + chunkIndex, expectedChunkList.toString(), actualChunkList.toString());
        runningIndex = nextIndex;
      }
      assertEquals("More events than needed", runningIndex, myLog.size());
    }
  }
}
