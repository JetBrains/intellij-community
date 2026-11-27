// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
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
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.project.ProjectKt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MultiModuleEditingTest {
  private static final String TEST_PATH = JavaTestUtil.getJavaTestDataPath() +
                                          "/moduleRootManager/multiModuleEditing".replace('/', File.separatorChar);
  @SuppressWarnings("deprecation")
  private final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private String myTestName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    myTestName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
    IdeaTestUtil.setProjectLanguageLevel(projectFixture.get(), LanguageLevel.JDK_1_6);
  }

  @Test
  public void testAddTwoModules() {
    final MessageBusConnection connection = projectFixture.get().getMessageBus().connect();
    final MyModuleListener moduleListener = new MyModuleListener();
    connection.subscribe(ModuleListener.TOPIC, moduleListener);
    final ModuleManager moduleManager = ModuleManager.getInstance(projectFixture.get());

    final Module moduleA;
    final Module moduleB;

    Path dir = ProjectKt.getStateStore(projectFixture.get()).getProjectBasePath();

    {
      final ModifiableModuleModel modifiableModel = moduleManager.getModifiableModel();
      moduleA = modifiableModel.newModule(dir.resolve("a.iml"), JavaModuleType.getModuleType().getId());
      moduleB = modifiableModel.newModule(dir.resolve("b.iml"), JavaModuleType.getModuleType().getId());
      assertEquals(0, moduleManager.getModules().length, "Changes are not applied until commit");
      //noinspection SSBasedInspection
      moduleListener.assertCorrectEvents(new String[0][]);
      WriteAction.runAndWait(modifiableModel::commit);
      IndexingTestUtil.waitUntilIndexesAreReady(projectFixture.get());
    }

    assertEquals(2, moduleManager.getModules().length);
    assertTrue(moduleManager.findModuleByName("a").equals(moduleA));
    assertTrue(moduleManager.findModuleByName("b").equals(moduleB));
    moduleListener.assertCorrectEvents(new String[][]{{"+a", "+b"}});

    {
      final ModifiableModuleModel modifiableModel = moduleManager.getModifiableModel();
      modifiableModel.disposeModule(moduleA);
      modifiableModel.disposeModule(moduleB);
      assertEquals(2, moduleManager.getModules().length, "Changes are not applied until commit");
      moduleListener.assertCorrectEvents(new String[][]{{"+a", "+b"}});
      WriteAction.runAndWait(modifiableModel::commit);
      IndexingTestUtil.waitUntilIndexesAreReady(projectFixture.get());
    }

    assertEquals(0, moduleManager.getModules().length);
    moduleListener.assertCorrectEvents(new String[][]{{"+a", "+b"}, {"-a", "-b"}});
    connection.disconnect();
  }

  @Test
  public void testRootsEditing() {
    final MessageBusConnection connection = projectFixture.get().getMessageBus().connect();
    final ModuleManager moduleManager = ModuleManager.getInstance(projectFixture.get());
    final MyModuleListener moduleListener = new MyModuleListener();
    connection.subscribe(ModuleListener.TOPIC, moduleListener);

    Path dir = ProjectKt.getStateStore(projectFixture.get()).getProjectBasePath();

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

      WriteAction.runAndWait(() -> ModifiableModelCommitter.multiCommit(new ModifiableRootModel[]{rootModelB, rootModelA}, moduleModel));
      IndexingTestUtil.waitUntilIndexesAreReady(projectFixture.get());
    }

    final JavaPsiFacade psiManager = JavaPsiFacade.getInstance(projectFixture.get());
    ReadAction.runBlocking(() -> {
      assertNull(psiManager.findClass("j.B", GlobalSearchScope.moduleWithDependenciesScope(moduleA)));
      assertNull(psiManager.findClass("q.A", GlobalSearchScope.moduleScope(moduleB)));

      assertNotNull(psiManager.findClass("q.A", GlobalSearchScope.moduleScope(moduleA)));
      assertNotNull(psiManager.findClass("q.A", GlobalSearchScope.moduleWithDependenciesScope(moduleB)));
      assertNotNull(psiManager.findClass("j.B", GlobalSearchScope.moduleScope(moduleB)));
      assertNotNull(psiManager.findClass("j.B", GlobalSearchScope.moduleWithDependenciesScope(moduleB)));
    });

    WriteAction.runAndWait(() -> moduleManager.disposeModule(moduleB));
    WriteAction.runAndWait(() -> moduleManager.disposeModule(moduleA));
    moduleListener.assertCorrectEvents(new String[][]{{"+b", "+a"}, {"-b"}, {"-a"}});

    connection.disconnect();
  }

  @Test
  public void testRenaming() throws Exception{
    final ModuleManager moduleManager = ModuleManager.getInstance(projectFixture.get());
    final Module moduleA;
    final Module moduleB;

    Path dir = ProjectKt.getStateStore(projectFixture.get()).getProjectBasePath();

    {
      final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      moduleA = moduleModel.newModule(dir.resolve("a.iml"), JavaModuleType.getModuleType().getId());
      moduleB = moduleModel.newModule(dir.resolve("b.iml"), JavaModuleType.getModuleType().getId());
      final Module moduleC = moduleModel.newModule(dir.resolve("c.iml"), JavaModuleType.getModuleType().getId());
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelB.addModuleOrderEntry(moduleC);
      moduleModel.disposeModule(moduleC);
      WriteAction.runAndWait(() -> ModifiableModelCommitter.multiCommit(new ModifiableRootModel[]{rootModelB}, moduleModel));
      IndexingTestUtil.waitUntilIndexesAreReady(projectFixture.get());
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
      WriteAction.runAndWait(() -> moduleModel.commit());
      IndexingTestUtil.waitUntilIndexesAreReady(projectFixture.get());
    }

    assertEquals(1, rootManagerB.getDependencies().length);
    assertEquals(moduleA, rootManagerB.getDependencies()[0]);
    assertSame(moduleA, moduleManager.findModuleByName("c"));
    assertNull(moduleManager.findModuleByName("a"));
    assertEquals("c", moduleA.getName());
    WriteAction.runAndWait(() -> moduleManager.disposeModule(moduleA));
    WriteAction.runAndWait(() -> moduleManager.disposeModule(moduleB));
  }

  private VirtualFile getVirtualFileInTestData(final String relativeVfsPath) {
    return WriteCommandAction.runWriteCommandAction(null, (Computable<VirtualFile>)() -> {
      final String path =
        TEST_PATH + File.separatorChar + myTestName + File.separatorChar + relativeVfsPath.replace('/', File.separatorChar);
      final VirtualFile result = StandardFileSystems.local().refreshAndFindFileByPath(new File(path).getAbsolutePath());
      assertNotNull(result, "File " + path + " doesn't exist");
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
        assertTrue(nextIndex <= myLog.size(), "Expected chunk " + expectedChunkList.toString());
        final List<String> actualChunkList = new ArrayList<>(myLog.subList(runningIndex, nextIndex));
        Collections.sort(expectedChunkList);
        Collections.sort(actualChunkList);
        assertEquals(expectedChunkList.toString(), actualChunkList.toString(), "Chunk " + chunkIndex);
        runningIndex = nextIndex;
      }
      assertEquals(runningIndex, myLog.size(), "More events than needed");
    }
  }
}
