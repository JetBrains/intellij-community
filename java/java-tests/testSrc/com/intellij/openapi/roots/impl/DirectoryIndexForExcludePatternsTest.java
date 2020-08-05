// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class DirectoryIndexForExcludePatternsTest extends DirectoryIndexTestCase {
  private VirtualFile myContentRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File root = createTempDirectory();
    myContentRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
    ModuleRootModificationUtil.addContentRoot(myModule, myContentRoot.getPath());
  }

  public void testExcludeFileByExtension() {
    /*
      root/
        dir/
          a.txt
          A.java
        src/     (module source root)
          a.txt
          A.java
        testSrc/ (module test source root)
          a.txt
          A.java
        a.txt
        A.java

      All *.txt files are excluded by pattern.
     */
    addExcludePattern("*.txt");
    VirtualFile dir = createChildDirectory(myContentRoot, "dir");
    VirtualFile src = createChildDirectory(myContentRoot, "src");
    PsiTestUtil.addSourceRoot(myModule, src);
    VirtualFile testSrc = createChildDirectory(myContentRoot, "testSrc");
    PsiTestUtil.addSourceRoot(myModule, testSrc, true);
    VirtualFile txt1 = createChildData(myContentRoot, "a.txt");
    VirtualFile txt2 = createChildData(dir, "a.txt");
    VirtualFile txt3 = createChildData(src, "a.txt");
    VirtualFile txt4 = createChildData(testSrc, "a.txt");
    VirtualFile java1 = createChildData(myContentRoot, "A.java");
    VirtualFile java2 = createChildData(dir, "A.java");
    VirtualFile java3 = createChildData(src, "A.java");
    VirtualFile java4 = createChildData(testSrc, "A.java");
    assertExcluded(txt1, myModule);
    assertExcluded(txt2, myModule);
    assertExcluded(txt3, myModule);
    assertExcluded(txt4, myModule);
    assertNotExcluded(java1);
    assertNotExcluded(java2);
    assertNotExcluded(java3);
    assertNotExcluded(java4);
    assertTrue(myFileIndex.isUnderSourceRootOfType(java3, Collections.singleton(JavaSourceRootType.SOURCE)));
    assertTrue(myFileIndex.isInTestSourceContent(java4));
    assertTrue(myFileIndex.isUnderSourceRootOfType(java4, Collections.singleton(JavaSourceRootType.TEST_SOURCE)));
    assertIteratedContent(myModule, Arrays.asList(java1, java2), Arrays.asList(txt1, txt2));
  }

  public void testExcludeDirectoryByName() {
    /*
      root/
        dir/
          a.txt
          exc/      <- excluded
            a.txt   <- excluded
        exc/        <- excluded
          a.txt     <- excluded
          dir2/     <- excluded
            a.txt   <- excluded
     */
    addExcludePattern("exc");
    VirtualFile dir = createChildDirectory(myContentRoot, "dir");
    VirtualFile exc = createChildDirectory(myContentRoot, "exc");
    VirtualFile dirUnderExc = createChildDirectory(exc, "dir2");
    VirtualFile excUnderDir = createChildDirectory(dir, "exc");
    VirtualFile underExc = createChildData(exc, "a.txt");
    VirtualFile underDir = createChildData(dir, "a.txt");
    VirtualFile underExcUnderDir = createChildData(excUnderDir, "a.txt");
    VirtualFile underDirUnderExc = createChildData(dirUnderExc, "a.txt");
    assertExcluded(exc, myModule);
    assertExcluded(underExc, myModule);
    assertExcluded(dirUnderExc, myModule);
    assertExcluded(underDirUnderExc, myModule);
    assertExcluded(underExcUnderDir, myModule);
    assertNotExcluded(dir);
    assertNotExcluded(underDir);
    assertIteratedContent(myModule, Collections.singletonList(underDir), Arrays.asList(underExc, underDirUnderExc, underExcUnderDir));
  }

  public void testIllegalArgumentInIsExcludedMethod() {
    addExcludePattern("xxx_excluded_directory");
    DirectoryInfo info = myIndex.getInfoForFile(myContentRoot);
    try {
      info.isExcluded(myContentRoot.getParent());
      fail("DirectoryInfo#isExcluded must fail because its argument is not under DirectoryInfo's root");
    }
    catch (IllegalArgumentException expected) {
    }
  }

  public void testExcludeFileFromLibrary() throws IOException {
    /*
      root/      (library root)
        dir/
          a.txt  <- excluded by pattern
        a.txt    <- excluded by pattern
        A.java
     */
    VirtualFile myLibraryRoot = getTempDir().createVirtualDir();
    VirtualFile dir = createChildDirectory(myLibraryRoot, "dir");
    VirtualFile txt1 = createChildData(myLibraryRoot, "a.txt");
    VirtualFile txt2 = createChildData(dir, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, file -> "a.txt".contentEquals(file.getNameSequence()));

    assertExcluded(txt1, null);
    assertNotInLibrarySources(txt1, null);

    assertExcluded(txt2, null);
    assertNotInLibrarySources(txt2, null);

    assertNotExcluded(java);
    assertInLibrarySources(java, null);

    assertIndexableContent(Collections.singletonList(java), Arrays.asList(txt1, txt2));
  }

  public void testExcludeDirectoryFromLibrary() throws IOException {
    /*
      root/      (library root)
        dir/     <- excluded directory
          a.txt
        a.txt
        A.java
     */
    VirtualFile myLibraryRoot = getTempDir().createVirtualDir();
    VirtualFile dir = createChildDirectory(myLibraryRoot, "dir");
    VirtualFile txt1 = createChildData(myLibraryRoot, "a.txt");
    VirtualFile txt2 = createChildData(dir, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, file -> "dir".contentEquals(file.getNameSequence()));

    assertExcluded(txt2, null);
    assertNotInLibrarySources(txt2, null);

    assertNotExcluded(txt1);
    assertInLibrarySources(txt1, null);
    assertNotExcluded(java);
    assertInLibrarySources(java, null);

    assertIndexableContent(Arrays.asList(java, txt1), Collections.singletonList(txt2));
  }

  public void testExcludeDirectoryFromLibraryThatIsUnderContentRoot() {
    /*
      root/         (content root)
        library/    (library root)
          dir/      <- excluded by pattern
            a.txt
          a.txt
          A.java
     */
    VirtualFile myLibraryRoot = createChildDirectory(myContentRoot, "library");
    VirtualFile dir = createChildDirectory(myLibraryRoot, "dir");
    VirtualFile txt1 = createChildData(myLibraryRoot, "a.txt");
    VirtualFile txt2 = createChildData(dir, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, file -> "dir".contentEquals(file.getNameSequence()));

    assertNotExcluded(txt2);
    assertNotInLibrarySources(txt2, myModule);

    assertNotExcluded(txt1);
    assertInLibrarySources(txt1, myModule);
    assertNotExcluded(java);
    assertInLibrarySources(java, myModule);

    assertIndexableContent(Arrays.asList(java, txt1, txt2), null);
  }

  public void testExcludeLibraryRoot() throws IOException {
    /*
      root/  (library root)  <- excluded library root
        a.txt
        A.java
     */
    VirtualFile myLibraryRoot = getTempDir().createVirtualDir();
    VirtualFile txt = createChildData(myLibraryRoot, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, file -> file.equals(myLibraryRoot));

    assertFalse(myIndex.getInfoForFile(txt).isInProject(txt));
    assertFalse(myIndex.getInfoForFile(java).isInProject(java));
  }

  public void testExcludeLibraryRootThatIsUnderContentRoot() {
    /*
      root/       (content root)
        library/  (library root) <- excluded library root
          a.txt
          A.java
     */
    VirtualFile myLibraryRoot = createChildDirectory(myContentRoot, "library");
    VirtualFile txt = createChildData(myLibraryRoot, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, file -> file.equals(myLibraryRoot));

    assertInProject(txt);
    assertNotInLibrarySources(txt, myModule);
    assertInProject(java);
    assertNotInLibrarySources(java, myModule);
    assertIndexableContent(Arrays.asList(txt, java), null);
  }

  public void testExcludeOnlyFiles() throws IOException {
    /*
      root/   (library root)
        dir/
        subdir/
          dir  (file that is named as directory)
     */
    VirtualFile myLibraryRoot = getTempDir().createVirtualDir();
    VirtualFile dir = createChildDirectory(myLibraryRoot, "dir");
    VirtualFile txt = createChildData(createChildDirectory(myLibraryRoot, "subdir"), "dir");
    registerLibrary(myLibraryRoot, file -> !file.isDirectory() && "dir".contentEquals(file.getNameSequence()));

    assertFalse(myIndex.getInfoForFile(txt).isInProject(txt));
    assertTrue(myIndex.getInfoForFile(dir).isInProject(dir));
  }

  private void addExcludePattern(@NotNull String pattern) {
    ModuleRootModificationUtil.updateModel(myModule,
                                           model -> MarkRootActionBase.findContentEntry(model, myContentRoot).addExcludePattern(pattern));
  }

  private void registerLibrary(@NotNull VirtualFile root, @Nullable Condition<VirtualFile> excludePattern) {
    WriteAction.run(() -> ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(
      () -> AdditionalLibraryRootsProvider.EP_NAME.getPoint().registerExtension(new AdditionalLibraryRootsProvider() {
              @NotNull
              @Override
              public Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
                return myProject == project ? Collections.singletonList(
                  SyntheticLibrary.newImmutableLibrary(Collections.singletonList(root), Collections.emptyList(), Collections.emptySet(), excludePattern)
                ) : Collections.emptyList();
              }
            }, getTestRootDisposable()), false, true));
  }
}
