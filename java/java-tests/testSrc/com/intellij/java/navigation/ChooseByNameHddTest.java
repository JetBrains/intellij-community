// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation;

import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile;
import com.intellij.platform.searchEverywhere.SeProviderIdUtils;
import com.intellij.platform.searchEverywhere.testFramework.SeProvidersTestFixture;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChooseByNameHddTest extends JavaCodeInsightFixtureTestCase {
  /** Tells which goto providers the test checks: the coroutine based ones, or the legacy based ones. */
  protected boolean isCoroutineBasedGoto() {
    return true;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setRegistryPropertyForTest(SeProvidersTestFixture.COROUTINE_BASED_GOTO_KEY, String.valueOf(isCoroutineBasedGoto()));
  }

  public void test_go_to_file_by_full_path() {
    PsiFile psiFile = myFixture.addFileToProject("foo/index.html", "foo");
    VirtualFile vFile = psiFile.getVirtualFile();
    String path = vFile.getPath();

    SeProvidersTestFixture fixture = createFixture(null);

    assertOrderedEquals(findFiles(fixture, path), Arrays.asList(psiFile));
    assertOrderedEquals(findFiles(fixture, FileUtil.toSystemDependentName(path)), Arrays.asList(psiFile));
    assertOrderedEquals(findFiles(fixture, vFile.getParent().getPath()), Arrays.asList(psiFile.getContainingDirectory()));
    assertOrderedEquals(findFiles(fixture, path + ":0"), new ArrayList<>(Arrays.asList(psiFile)));
  }

  public void test_prefer_same_named_classes_visible_in_current_module() throws IOException {
    int moduleCount = 10;

    List<Module> modules = new ArrayList<>();
    for (int i = 0; i < moduleCount; i++) {
      modules.add(
        PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod" + i, myFixture.getTempDirFixture().findOrCreateDir("mod" + i)));
    }
    ModuleRootModificationUtil.addDependency(myFixture.getModule(), modules.get(2));

    for (int i = 0; i < moduleCount; i++) {
      myFixture.addFileToProject("mod" + i + "/Foo.java", "class Foo {}");
    }

    PsiFile place = myFixture.addClass("class A {}").getContainingFile();
    SeProvidersTestFixture fixture = createFixture(place);

    List<String> resultModules = ContainerUtil.map(findFiles(fixture, "Foo"),
                                                   e -> ModuleUtilCore.findModuleForPsiElement((PsiElement)e).getName());

    assertEquals("mod2", resultModules.get(0));
  }

  public void test_paths_relative_to_topmost_module() throws IOException {
    PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "m1", myFixture.getTempDirFixture().findOrCreateDir("foo"));
    PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "m2", myFixture.getTempDirFixture().findOrCreateDir("foo/bar"));

    PsiFile file = myFixture.addFileToProject("foo/bar/goo/doo.txt", "");
    SeProvidersTestFixture fixture = createFixture(file);
    assertOrderedEquals(findFiles(fixture, "doo"), Arrays.asList(file));
    assertOrderedEquals(findFiles(fixture, "goo/doo"), Arrays.asList(file));
    assertOrderedEquals(findFiles(fixture, "bar/goo/doo"), Arrays.asList(file));
    assertOrderedEquals(findFiles(fixture, "foo/bar/goo/doo"), Arrays.asList(file));
  }

  public void test_source_test_resources_priority() throws IOException {
    final VirtualFile srcDir = myFixture.getTempDirFixture().findOrCreateDir("src");
    final VirtualFile resourcesDir = myFixture.getTempDirFixture().findOrCreateDir("resources");
    final VirtualFile testSrcDir = myFixture.getTempDirFixture().findOrCreateDir("test");

    PsiTestUtil.addSourceRoot(getModule(), srcDir, JavaSourceRootType.SOURCE);
    PsiTestUtil.addSourceRoot(getModule(), testSrcDir, JavaSourceRootType.TEST_SOURCE);
    PsiTestUtil.addSourceRoot(getModule(), resourcesDir, JavaResourceRootType.RESOURCE);
    PsiTestUtil.removeSourceRoot(getModule(), myFixture.getTempDirFixture().findOrCreateDir(""));

    PsiFile testSrcFile1 = myFixture.addFileToProject(testSrcDir.getName() + "/fileForSearch.txt", "");
    PsiFile testSrcFile2 = myFixture.addFileToProject(testSrcDir.getName() + "/sub/fileForSearch.txt", "");
    PsiFile srcFile1 = myFixture.addFileToProject(srcDir.getName() + "/sub/fileForSearch.txt", "");
    PsiFile srcFile2 = myFixture.addFileToProject(srcDir.getName() + "/sub/sub/fileForSearch.txt", "");
    PsiFile resourcesFile = myFixture.addFileToProject(resourcesDir.getName() + "/fileForSearch.txt", "");

    PsiFile contextFile = myFixture.addFileToProject("context.txt", "");
    PsiFile contextFile2 = myFixture.addFileToProject(testSrcDir.getName() + "/context.txt", "");

    //tests have low priority because of com.intellij.psi.util.proximity.InResolveScopeWeigher
    assertOrderedEquals(gotoFile("fileForSearch.txt", false, contextFile),
                        Arrays.asList(srcFile1, srcFile2, resourcesFile, testSrcFile1, testSrcFile2));

    //tests have high priority because of search from same root
    assertOrderedEquals(gotoFile("fileForSearch.txt", false, contextFile2),
                        Arrays.asList(testSrcFile1, testSrcFile2, srcFile1, srcFile2, resourcesFile));
  }

  public void test_go_to_file_by_absolute_path_outside_project() throws IOException {
    File outsideFile = createFileOutsideProject();

    SeProvidersTestFixture fixture = createFixture(null);

    String path = FileUtil.toSystemIndependentName(outsideFile.getPath());
    assertSameFile(outsideFile, assertOneElement(findFiles(fixture, path)));
    assertSameFile(outsideFile, assertOneElement(findFiles(fixture, FileUtil.toSystemDependentName(path))));
    assertSameFile(outsideFile, assertOneElement(findFiles(fixture, path + ":12")));
  }

  public void test_searching_by_absolute_path_does_not_cache_the_file_in_vfs() throws IOException {
    File outsideFile = createFileOutsideProject();
    String path = FileUtil.toSystemIndependentName(outsideFile.getPath());
    assertNull("precondition: the file must not be in VFS yet", LocalFileSystem.getInstance().findFileByPathIfCached(path));

    SeProvidersTestFixture fixture = createFixture(null);
    VirtualFile found = assertInstanceOf(assertOneElement(findFiles(fixture, path)), PsiFileSystemItem.class).getVirtualFile();

    CacheAvoidingVirtualFile cacheAvoiding = assertInstanceOf(found, CacheAvoidingVirtualFile.class);
    assertFalse(cacheAvoiding.isCached());
    assertNull("searching must not put the file into VFS", LocalFileSystem.getInstance().findFileByPathIfCached(path));

    // the file becomes a regular VFS file only when the user actually opens it, see SearchEverywhereNavigationHandler
    assertNotNull(cacheAvoiding.asCacheable());
    assertNotNull(LocalFileSystem.getInstance().findFileByPathIfCached(path));
  }

  public void test_repeated_search_by_absolute_path_yields_the_same_psi() throws IOException {
    File outsideFile = createFileOutsideProject();
    String path = FileUtil.toSystemIndependentName(outsideFile.getPath());

    SeProvidersTestFixture fixture = createFixture(null);
    Object first = assertOneElement(findFiles(fixture, path));
    Object second = assertOneElement(findFiles(fixture, path));

    // cache-avoiding files for the same path are equal, so PSI for them is reused instead of being recreated per keystroke
    assertEquals(((PsiFileSystemItem)first).getVirtualFile(), ((PsiFileSystemItem)second).getVirtualFile());
    assertSame(first, second);
  }

  public void test_go_to_file_by_path_starting_with_user_home() throws IOException {
    if (SystemInfo.isWindows) return; // '~' is not expanded on Windows

    File outsideFile = createFileOutsideProject();
    String oldUserHome = System.getProperty("user.home");
    System.setProperty("user.home", outsideFile.getParent());
    try {
      SeProvidersTestFixture fixture = createFixture(null);

      assertSameFile(outsideFile, assertOneElement(findFiles(fixture, "~/" + outsideFile.getName())));
      assertSameFile(outsideFile.getParentFile(), assertOneElement(findFiles(fixture, "~/")));
    }
    finally {
      System.setProperty("user.home", oldUserHome);
    }
  }

  public void test_go_to_directory_by_absolute_path_outside_project() throws IOException {
    File outsideFile = createFileOutsideProject();
    File outsideDir = outsideFile.getParentFile();

    SeProvidersTestFixture fixture = createFixture(null);

    Object item = assertOneElement(findFiles(fixture, FileUtil.toSystemIndependentName(outsideDir.getPath())));
    assertInstanceOf(item, PsiDirectory.class);
    assertSameFile(outsideDir, item);
  }

  public void test_go_to_excluded_file_by_absolute_path() throws IOException {
    VirtualFile excludedDir = myFixture.getTempDirFixture().findOrCreateDir("excluded");
    PsiFile excludedFile = myFixture.addFileToProject("excluded/hidden.txt", "");
    PsiTestUtil.addExcludedRoot(getModule(), excludedDir);

    SeProvidersTestFixture fixture = createFixture(null);

    String path = excludedFile.getVirtualFile().getPath();
    assertSameFile(new File(path), assertOneElement(findFiles(fixture, path)));
  }

  public void test_no_results_for_non_existent_absolute_path() {
    SeProvidersTestFixture fixture = createFixture(null);

    assertEmpty(findFiles(fixture, "/definitely/not/an/existing/path.txt"));
  }

  public void test_no_results_for_the_filesystem_root() {
    SeProvidersTestFixture fixture = createFixture(null);

    // the root is not a meaningful exact match, and as one it would outrank every real candidate
    assertEmpty(findFiles(fixture, "/"));
    assertEmpty(findFiles(fixture, "//"));
  }

  public void test_absolute_paths_outside_project_can_be_disabled() throws IOException {
    Registry.get("search.everywhere.absolute.path.outside.project").setValue(false, getTestRootDisposable());
    File outsideFile = createFileOutsideProject();

    SeProvidersTestFixture fixture = createFixture(null);

    assertEmpty(findFiles(fixture, FileUtil.toSystemIndependentName(outsideFile.getPath())));
  }

  private File createFileOutsideProject() throws IOException {
    File dir = FileUtil.createTempDirectory(getTestName(true), null, true);
    File file = new File(dir, "outside.txt");
    FileUtil.writeToFile(file, "");
    return file;
  }

  /** Compares canonical paths: temp directories may be reached via symlinks (e.g. {@code /var} on macOS). */
  private static void assertSameFile(File expected, Object actualItem) throws IOException {
    VirtualFile actual = assertInstanceOf(actualItem, PsiFileSystemItem.class).getVirtualFile();
    assertEquals(expected.getCanonicalFile(), new File(actual.getPath()).getCanonicalFile());
  }

  /** Opens a new Search Everywhere session. Its providers go away with the test. */
  private SeProvidersTestFixture createFixture(@Nullable PsiElement context) {
    return SeProvidersTestFixture.createBlocking(getProject(), getTestRootDisposable(), ObjectUtils.tryCast(context, PsiFile.class));
  }

  private static List<?> findFiles(SeProvidersTestFixture fixture, String text) {
    return fixture.findElementsBlocking(SeProviderIdUtils.FILES_ID, text);
  }

  @SuppressWarnings("unchecked")
  private List<PsiFile> gotoFile(String text, boolean checkboxState, PsiElement context) {
    List<?> files = createFixture(context).findElementsBlocking(SeProviderIdUtils.FILES_ID, text, checkboxState);
    return (List<PsiFile>)files;
  }
}
