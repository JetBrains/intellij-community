// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.impl.ProjectRootUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UpdateCacheTest extends JavaPsiTestCase {
  @Override
  protected void setUpProject() {
    loadAndSetupProject(getProjectDirOrFile());
  }

  private void loadAndSetupProject(@NotNull Path path) {
    myProject = PlatformTestUtil.loadAndOpenProject(path, getTestRootDisposable());

    setUpModule();

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/updateCache";
    createTestProjectStructure(root);

    setUpJdk();
  }

  public void testFileCreation() {
    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];

    PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText("New.java", JavaFileType.INSTANCE, "class A{ Object o;}");
    final PsiFile finalFile = file;
    file = WriteAction.compute(() -> (PsiFile)root.add(finalFile));
    assertNotNull(file);

    PsiClass objectClass = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(getProject()));
    assertNotNull(objectClass);
    checkUsages(objectClass, new String[]{"New.java"});
  }

  public void testExternalFileCreation() throws Exception {
    VirtualFile root = ProjectRootManager.getInstance(myProject).getContentRoots()[0];

    String newFilePath = root.getPresentableUrl() + File.separatorChar + "New.java";
    FileUtil.writeToFile(new File(newFilePath), "class A{ Object o;}".getBytes(StandardCharsets.UTF_8));
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(newFilePath.replace(File.separatorChar, '/'));
    assertNotNull(file);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiClass objectClass = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(getProject()));
    assertNotNull(objectClass);
    checkUsages(objectClass, new String[]{"New.java"});
  }

  public void testExternalFileDeletion() {
    VirtualFile root = ProjectRootManager.getInstance(myProject).getContentRoots()[0];

    VirtualFile file = root.findChild("1.java");
    assertNotNull(file);
    delete(file);

    PsiClass stringClass = myJavaFacade.findClass("java.lang.String", GlobalSearchScope.allScope(getProject()));
    assertNotNull(stringClass);
    checkUsages(stringClass, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public void testExternalFileModification() {
    VirtualFile root = ProjectRootManager.getInstance(myProject).getContentRoots()[0];

    VirtualFile file = root.findChild("1.java");
    assertNotNull(file);
    setFileText(file, "class A{ Object o;}");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiClass objectClass = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(getProject()));
    assertNotNull(objectClass);
    checkUsages(objectClass, new String[]{"1.java"});
  }

  public void testExternalFileModificationWhileProjectClosed() throws Exception {
    VirtualFile root = ProjectRootManager.getInstance(myProject).getContentRoots()[0];

    PsiClass objectClass = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(getProject()));
    assertNotNull(objectClass);
    checkUsages(objectClass, ArrayUtil.EMPTY_STRING_ARRAY);
    FileBasedIndex.getInstance().ensureUpToDate(TodoIndex.NAME, getProject(), GlobalSearchScope.allScope(getProject()));

    String projectLocation = myProject.getPresentableUrl();
    assert projectLocation != null : myProject;
    PlatformTestUtil.saveProject(myProject);
    VirtualFile content = ModuleRootManager.getInstance(getModule()).getContentRoots()[0];
    Project project = myProject;
    ProjectManagerEx.getInstanceEx().forceCloseProject(project);
    myProject = null;
    InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project);

    assertTrue("Project was not disposed", project.isDisposed());
    myModule = null;

    final File file = new File(root.getPath(), "1.java");
    assertTrue(file.exists());

    FileUtil.writeToFile(file, "class A{ Object o;}".getBytes(StandardCharsets.UTF_8));
    root.refresh(false, true);

    LocalFileSystem.getInstance().refresh(false);
    Set<String> rootChildren = Stream.of(root.getChildren()).map(f -> f.getName()).collect(Collectors.toSet());
    assertTrue(rootChildren.contains("1.java"));

    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get(projectLocation), getTestRootDisposable());
    InjectedLanguageManagerImpl.pushInjectors(getProject());

    setUpModule();
    setUpJdk();
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    PsiTestUtil.addSourceContentToRoots(getModule(), content);

    assertNotNull(myProject);
    myPsiManager = (PsiManagerImpl) PsiManager.getInstance(myProject);
    myJavaFacade = JavaPsiFacadeEx.getInstanceEx(myProject);

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    objectClass = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, scope);
    assertNotNull(objectClass);

    Set<String> filesWithObject =
      FileBasedIndex
        .getInstance()
        .getContainingFiles(IdIndex.NAME, new IdIndexEntry("Object", true), scope)
        .stream().map(f -> f.getName()).collect(Collectors.toSet());
    assertTrue(filesWithObject.contains("1.java"));

    checkUsages(objectClass, new String[]{"1.java"});
  }

  public void testExternalDirCreation() throws Exception {
    VirtualFile root = ProjectRootManager.getInstance(myProject).getContentRoots()[0];

    String newFilePath = root.getPresentableUrl() + File.separatorChar + "dir" + File.separatorChar + "New.java";
    LOG.assertTrue(new File(newFilePath).getParentFile().mkdir());
    FileUtil.writeToFile(new File(newFilePath), "class A{ Object o;}".getBytes(StandardCharsets.UTF_8));
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(newFilePath.replace(File.separatorChar, '/'));
    assertNotNull(file);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiClass objectClass = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(getProject()));
    assertNotNull(objectClass);
    checkUsages(objectClass, new String[]{"New.java"});
  }

  public void testExternalDirDeletion() {
    VirtualFile root = ProjectRootManager.getInstance(myProject).getContentRoots()[0];

    VirtualFile file = root.findChild("aDir");
    assertNotNull(file);
    delete(file);

    PsiClass threadClass = myJavaFacade.findClass("java.lang.Thread", GlobalSearchScope.allScope(getProject()));
    assertNotNull(threadClass);
    checkUsages(threadClass, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public void testTodoConfigurationChange() {
    TodoPattern pattern = new TodoPattern("newtodo", TodoAttributesUtil.createDefault(), true);
    TodoPattern[] oldPatterns = TodoConfiguration.getInstance().getTodoPatterns();

    checkTodos(new String[]{"2.java"});

    TodoConfiguration.getInstance().setTodoPatterns(new TodoPattern[]{pattern});
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

    try{
      checkTodos(new String[]{"1.java"});
    }
    finally{
      TodoConfiguration.getInstance().setTodoPatterns(oldPatterns);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      checkTodos(new String[]{"2.java"});
    }
  }

  public void testAddExcludeRoot() {
    PsiTodoSearchHelper.SERVICE.getInstance(myProject).findFilesWithTodoItems(); // to initialize caches

    ProjectRootManagerEx rootManager = (ProjectRootManagerEx)ProjectRootManager.getInstance(myProject);
    final VirtualFile root = rootManager.getContentRoots()[0];

    final VirtualFile dir = root.findChild("aDir");

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile newFile = createChildData(dir, "New.java");
      setFileText(newFile, "class A{ Exception e;} //todo");
    });

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiTestUtil.addExcludedRoot(myModule, dir);

    PsiClass exceptionClass = myJavaFacade.findClass("java.lang.Exception", GlobalSearchScope.allScope(getProject()));
    assertNotNull(exceptionClass);
    checkUsages(exceptionClass, new String[]{"1.java"});
    checkTodos(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void testRemoveExcludeRoot() {
    ProjectRootManagerEx rootManager = (ProjectRootManagerEx)ProjectRootManager.getInstance(myProject);
    final VirtualFile root = rootManager.getContentRoots()[0];

    final VirtualFile dir = root.findChild("aDir");

    PsiTestUtil.addExcludedRoot(myModule, dir);

    PsiTodoSearchHelper.SERVICE.getInstance(myProject).findFilesWithTodoItems(); // to initialize caches

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile newFile = createChildData(dir, "New.java");
      setFileText(newFile, "class A{ Exception e;} //todo");
    });

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiTodoSearchHelper.SERVICE.getInstance(myProject).findFilesWithTodoItems(); // to update caches

    PsiTestUtil.removeExcludedRoot(myModule, dir);

    PsiClass exceptionClass = myJavaFacade.findClass("java.lang.Exception", GlobalSearchScope.allScope(getProject()));
    assertNotNull(exceptionClass);
    checkUsages(exceptionClass, new String[]{"1.java", "2.java", "New.java"});
    checkTodos(new String[]{"2.java", "New.java"});
  }

  public void testAddSourceRoot() throws Exception {
    File dir = createTempDirectory();

    final VirtualFile root =
      LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      PsiTestUtil.addContentRoot(myModule, root);

      VirtualFile newFile = createChildData(root, "New.java");
      setFileText(newFile, "class A{ Exception e;} //todo");
    });

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiTodoSearchHelper.SERVICE.getInstance(myProject).findFilesWithTodoItems(); // to initialize caches

    PsiTestUtil.addSourceRoot(myModule, root);

    PsiClass exceptionClass = myJavaFacade.findClass("java.lang.Exception", GlobalSearchScope.allScope(getProject()));
    assertNotNull(exceptionClass);
    checkUsages(exceptionClass, new String[]{"1.java", "2.java", "New.java"});
    checkTodos(new String[]{"2.java", "New.java"});
  }

  public void testRemoveSourceRoot() {
    final VirtualFile root = ModuleRootManager.getInstance(myModule).getContentRoots()[0];

    PsiTodoSearchHelper.SERVICE.getInstance(myProject).findFilesWithTodoItems(); // to initialize caches

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile newFile = createChildData(root, "New.java");
      setFileText(newFile, "class A{ Exception e;} //todo");
    });

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiTodoSearchHelper.SERVICE.getInstance(myProject).findFilesWithTodoItems(); // to update caches

    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(myModule).getSourceRoots();
    LOG.assertTrue(sourceRoots.length == 1);
    PsiTestUtil.removeSourceRoot(myModule, sourceRoots[0]);


    PsiClass exceptionClass = myJavaFacade.findClass("java.lang.Exception", GlobalSearchScope.allScope(getProject()));
    assertNotNull(exceptionClass);
    // currently it actually finds usages by FQN due to Java PSI enabled for out-of-source java files
    // so the following check is disabled
    //checkUsages(exceptionClass, new String[]{});
    checkTodos(new String[]{"2.java", "New.java"});
  }

  public void testAddProjectRoot() throws Exception {
    File dir = createTempDirectory();

    final VirtualFile root =
      LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      PsiTestUtil.addSourceRoot(myModule, root);

      VirtualFile newFile = createChildData(root, "New.java");
      setFileText(newFile, "class A{ Exception e;} //todo");
    });

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiSearchHelper.getInstance(myProject).processAllFilesWithWord("aaa", GlobalSearchScope.allScope(myProject), psiFile -> true, true); // to initialize caches

/*
    rootManager.startChange();
    rootManager.addRoot(root, ProjectRootType.PROJECT);
    rootManager.finishChange();
*/

    PsiClass exceptionClass = myJavaFacade.findClass("java.lang.Exception", GlobalSearchScope.allScope(getProject()));
    assertNotNull(exceptionClass);
    checkUsages(exceptionClass, new String[]{"1.java", "2.java", "New.java"});
    checkTodos(new String[]{"2.java", "New.java"});
  }

  public void testSCR6066() {
    ProjectRootManagerEx rootManager = (ProjectRootManagerEx)ProjectRootManager.getInstance(myProject);
    final VirtualFile root = rootManager.getContentRoots()[0];

    PsiTodoSearchHelper.SERVICE.getInstance(myProject).findFilesWithTodoItems(); // to initialize caches

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile newFile = createChildData(root, "New.java");
      setFileText(newFile, "class A{ Exception e;} //todo");
    });

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiTodoSearchHelper.SERVICE.getInstance(myProject).findFilesWithTodoItems(); // to update caches

    PsiTestUtil.addExcludedRoot(myModule, root);

    PsiClass exceptionClass = myJavaFacade.findClass("java.lang.Exception", GlobalSearchScope.allScope(getProject()));
    assertNotNull(exceptionClass);
    checkUsages(exceptionClass, ArrayUtil.EMPTY_STRING_ARRAY);
    checkTodos(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void checkUsages(PsiElement element, @NonNls String[] expectedFiles){
    PsiReference[] refs = ReferencesSearch.search(element, GlobalSearchScope.projectScope(myProject), false).toArray(
      PsiReference.EMPTY_ARRAY);

    List<PsiFile> files = new ArrayList<>();
    for (PsiReference ref : refs) {
      PsiFile file = ref.getElement().getContainingFile();
      if (!files.contains(file)) {
        files.add(file);
      }
    }

    assertEquals(Arrays.toString(expectedFiles) + ".length != " + files + ".size()", expectedFiles.length, files.size());

    Collections.sort(files, Comparator.comparing(PsiFileSystemItem::getName));
    Arrays.sort(expectedFiles);

    for(int i = 0; i < expectedFiles.length; i++){
      String name = expectedFiles[i];
      PsiFile file = files.get(i);
      assertEquals(name, file.getName());
    }
  }

  private void checkTodos(@NonNls String[] expectedFiles){
    PsiTodoSearchHelper helper = PsiTodoSearchHelper.SERVICE.getInstance(myProject);

    PsiFile[] files = helper.findFilesWithTodoItems();

    assertEquals(expectedFiles.length, files.length);

    Arrays.sort(files, Comparator.comparing(PsiFileSystemItem::getName));
    Arrays.sort(expectedFiles);

    for(int i = 0; i < expectedFiles.length; i++){
      String name = expectedFiles[i];
      PsiFile file = files[i];
      assertEquals(name, file.getName());
    }
  }
}
