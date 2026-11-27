// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildData;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.setFileText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class IdCacheTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile myRootDir;
  private Project myProject;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    myProject = fixture.getProject();
    String root = JavaTestUtil.getJavaTestDataPath()+ "/psi/impl/cache/";

    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk17());
    myRootDir = fixture.createTestProjectStructure(root);
  }

  @Test
  public void testBuildCache() {
    checkCache(CacheManager.getInstance(myProject), TodoCacheManager.getInstance(myProject));
  }

  @Test
  public void testLoadCacheNoTodo() {

    final CacheManager cache = CacheManager.getInstance(myProject);

    ReadAction.runBlocking(() -> {
      checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, GlobalSearchScope.projectScope(myProject), false)));
    });
  }

  @Test
  public void testUpdateCache1() throws InterruptedException {
    createChildData(myRootDir, "4.java");
    Thread.sleep(1000);
    checkCache(CacheManager.getInstance(myProject), TodoCacheManager.getInstance(myProject));
  }

  @Test
  public void testUpdateCache2() {
    VirtualFile child = myRootDir.findChild("1.java");
    setFileText(child, "xxx");

    EdtTestUtil.runInEdtAndWait(() -> {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      FileDocumentManager.getInstance().saveAllDocuments();
    });

    ReadAction.runBlocking(() -> {
      final CacheManager cache = CacheManager.getInstance(myProject);
      final TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      checkResult(new String[] {"1.java"}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY,scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY,scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY,scope, false)));
      checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY,scope, false)));

      checkResult(new String[]{"3.java"}, convert(getFilesWithTodoItems(todocache)));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
    });
  }

  @Test
  public void testUpdateCache3() {
    VirtualFile child = myRootDir.findChild("1.java");
    VfsTestUtil.deleteFile(child);

    ReadAction.runBlocking(() -> {
      final CacheManager cache2 = CacheManager.getInstance(myProject);
      final TodoCacheManager todocache2 = TodoCacheManager.getInstance(myProject);
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      checkResult(ArrayUtilRt.EMPTY_STRING_ARRAY, convert(cache2.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
      checkResult(ArrayUtilRt.EMPTY_STRING_ARRAY, convert(cache2.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java"}, convert(cache2.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache2.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache2.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"3.java"}, convert(cache2.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

      checkResult(new String[]{"3.java"}, convert(getFilesWithTodoItems(todocache2)));
      assertEquals(0, todocache2.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(2, todocache2.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
    });
  }

  @Test
  public void testUpdateCacheNoTodo() {
    createChildData(myRootDir, "4.java");
    ReadAction.runBlocking(() -> {
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      final CacheManager cache = CacheManager.getInstance(myProject);
      checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    });
  }

  @Test
  public void testUpdateOnTodoChange() {
    TodoPattern pattern = new TodoPattern("newtodo", TodoAttributesUtil.createDefault(), true);
    TodoPattern[] oldPatterns = TodoConfiguration.getInstance().getTodoPatterns();
    EdtTestUtil.runInEdtAndWait(() -> {
      TodoConfiguration.getInstance().setTodoPatterns(new TodoPattern[]{pattern});
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    });

    try{
      final TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
      ReadAction.runBlocking(() -> {
        checkResult(new String[]{"2.java"}, convert(getFilesWithTodoItems(todocache)));
        assertEquals(0, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
        assertEquals(1, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
        assertEquals(0, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
      });
    }
    finally{
      EdtTestUtil.runInEdtAndWait(() -> TodoConfiguration.getInstance().setTodoPatterns(oldPatterns));
    }
  }

  @Test
  public void testFileModification() {
    final CacheManager cache = CacheManager.getInstance(myProject);
    final TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
    checkCache(cache, todocache);

    VirtualFile child = myRootDir.findChild("1.java");

    checkCache(cache, todocache);

    setFileText(child, "xxx");
    setFileText(child, "xxx");
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());

    ReadAction.runBlocking(() -> {
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      checkResult(new String[] {"1.java"}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

      checkResult(new String[]{"3.java"}, convert(getFilesWithTodoItems(todocache)));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
    });
  }

  @Test
  public void testFileDeletion() {
    final CacheManager cache = CacheManager.getInstance(myProject);
    final TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
    checkCache(cache, todocache);

    VirtualFile child = myRootDir.findChild("1.java");
    VfsTestUtil.deleteFile(child);

    ReadAction.runBlocking(() -> {
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      checkResult(new String[]{}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

      checkResult(new String[]{"3.java"}, convert(getFilesWithTodoItems(todocache)));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
    });
  }

  @Test
  public void testFileCreation() {
    final CacheManager cache = CacheManager.getInstance(myProject);
    final TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
    checkCache(cache, todocache);

    VirtualFile child = createChildData(myRootDir, "4.java");
    setFileText(child, "xxx //todo");
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());

    ReadAction.runBlocking(() -> {
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      checkResult(new String[]{"4.java"}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"1.java"}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"1.java", "2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

      checkResult(new String[]{"1.java", "3.java", "4.java"}, convert(getFilesWithTodoItems(todocache)));
      assertEquals(1, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(1, todocache.getTodoCount(myRootDir.findChild("4.java"), TodoIndexPatternProvider.getInstance()));
    });
  }

  @Test
  public void testCrash() {
    final CacheManager cache = CacheManager.getInstance(myProject);
    ReadAction.runBlocking(() -> cache.getFilesWithWord("xxx", UsageSearchContext.ANY, GlobalSearchScope.projectScope(myProject), false));
    System.gc();
  }

  @Test
  public void testTodoCountUnknownForFileOutsideProject() {
    VirtualFile file = createFileWithTwoTodos(heavyFixture.get().createTempVirtualDir());
    TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
    ReadAction.runBlocking(() -> {
      assertEquals(-1, todocache.getTodoCount(file, TodoIndexPatternProvider.getInstance()));
      TodoPattern pattern = TodoConfiguration.getInstance().getTodoPatterns()[0];
      assertEquals(-1, todocache.getTodoCount(file, pattern.getIndexPattern()));

      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      assertNotNull(psiFile);
      PsiTodoSearchHelper helper = PsiTodoSearchHelper.getInstance(myProject);
      assertEquals(2, helper.findTodoItems(psiFile).length);
      assertEquals(2, helper.getTodoItemsCount(psiFile));

      checkResult(new String[]{"1.java", "3.java"}, convert(getFilesWithTodoItems(todocache)));
    });
  }

  @Test
  public void testTodoCountZeroForLibrarySourceFile() {
    VirtualFile libDir = heavyFixture.get().createTempVirtualDir();
    PsiTestUtil.addProjectLibrary(heavyFixture.get().getModule(), "lib", List.of(), List.of(libDir));
    VirtualFile file = createFileWithTwoTodos(libDir);

    assertEquals(0, ReadAction.computeBlocking(
      () -> TodoCacheManager.getInstance(myProject).getTodoCount(file, TodoIndexPatternProvider.getInstance())));
  }

  @Test
  public void testTodoCountZeroForExcludedFile() {
    VirtualFile excludedDir = createChildDirectory(myRootDir, "excluded");
    PsiTestUtil.addExcludedRoot(heavyFixture.get().getModule(), excludedDir);
    VirtualFile file = createFileWithTwoTodos(excludedDir);

    assertEquals(0, ReadAction.computeBlocking(
      () -> TodoCacheManager.getInstance(myProject).getTodoCount(file, TodoIndexPatternProvider.getInstance())));
  }

  private @NotNull VirtualFile createFileWithTwoTodos(@NotNull VirtualFile dir) {
    VirtualFile file = createChildData(dir, "A.java");
    setFileText(file, "class A {\n  // TODO first\n  // TODO second\n}\n");
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());
    return file;
  }

  private void checkCache(CacheManager cache, TodoCacheManager todocache) {
    ReadAction.runBlocking(() -> {
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      checkResult(ArrayUtilRt.EMPTY_STRING_ARRAY, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"1.java"}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"1.java", "2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
      checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

      checkResult(new String[]{"1.java", "3.java"}, convert(getFilesWithTodoItems(todocache)));
      assertEquals(1, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
    });
  }
  
  private static PsiFile @NotNull [] getFilesWithTodoItems(@NotNull TodoCacheManager todocache) {
    HashSet<PsiFile> files = new HashSet<>();
    todocache.processFilesWithTodoItems(new CommonProcessors.CollectProcessor<>(files));
    return PsiUtilCore.toPsiFileArray(files);
  } 

  private static VirtualFile[] convert(PsiFile[] psiFiles) {
    final VirtualFile[] files = new VirtualFile[psiFiles.length];
    for (int idx = 0; idx < psiFiles.length; idx++) {
      files[idx] = psiFiles[idx].getVirtualFile();
    }
    return files;
  }

  private static void checkResult(String[] expected, VirtualFile[] result){
    assertEquals(expected.length, result.length);
    Arrays.sort(expected);
    Arrays.sort(result, Comparator.comparing(VirtualFile::getName));

    for(int i = 0; i < expected.length; i++){
      String name = expected[i];
      assertEquals(name, result[i].getName());
    }
  }
}
