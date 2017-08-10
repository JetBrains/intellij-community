/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
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
    boolean successeded = false;
    try {
      info.isExcluded(myContentRoot.getParent());
      successeded = true;
    }
    catch (AssertionError ignored) {
    }
    assertFalse("DirectoryInfo#isExcluded must fail it its argument is not under DirectoryInfo's root", successeded);
  }

  public void testExcludeFileFromLibrary() throws IOException {
    VirtualFile myLibraryRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory());
    VirtualFile dir = createChildDirectory(myLibraryRoot, "dir");
    VirtualFile txt1 = createChildData(myLibraryRoot, "a.txt");
    VirtualFile txt2 = createChildData(dir, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, fileName -> "a.txt".contentEquals(fileName));

    assertExcluded(txt1, null);
    assertNotInLibrarySources(txt1, null);

    assertExcluded(txt2, null);
    assertNotInLibrarySources(txt2, null);

    assertNotExcluded(java);
    assertInLibrarySources(java, null);

    assertIndexableContent(Collections.singletonList(java), Arrays.asList(txt1, txt2));
  }

  public void testExcludeDirectoryFromLibrary() throws IOException {
    VirtualFile myLibraryRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory());
    VirtualFile dir = createChildDirectory(myLibraryRoot, "dir");
    VirtualFile txt1 = createChildData(myLibraryRoot, "a.txt");
    VirtualFile txt2 = createChildData(dir, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, fileName -> "dir".contentEquals(fileName));

    assertExcluded(txt2, null);
    assertNotInLibrarySources(txt2, null);

    assertNotExcluded(txt1);
    assertInLibrarySources(txt1, null);
    assertNotExcluded(java);
    assertInLibrarySources(java, null);

    assertIndexableContent(Collections.singletonList(java), Collections.singletonList(txt2));
  }

  public void testExcludeDirectoryFromLibraryThatIsUnderContentRoot() {
    VirtualFile myLibraryRoot = createChildDirectory(myContentRoot, "library");
    VirtualFile dir = createChildDirectory(myLibraryRoot, "dir");
    VirtualFile txt1 = createChildData(myLibraryRoot, "a.txt");
    VirtualFile txt2 = createChildData(dir, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, fileName -> "dir".contentEquals(fileName));

    assertNotExcluded(txt2);
    assertNotInLibrarySources(txt2, myModule);

    assertNotExcluded(txt1);
    assertInLibrarySources(txt1, myModule);
    assertNotExcluded(java);
    assertInLibrarySources(java, myModule);

    assertIndexableContent(Arrays.asList(java, txt1, txt2), null);
  }

  public void testExcludeLibraryRoot() throws IOException {
    VirtualFile myLibraryRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory());
    VirtualFile txt = createChildData(myLibraryRoot, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, fileName -> fileName.equals(myLibraryRoot.getNameSequence()));

    assertFalse(myIndex.getInfoForFile(txt).isInProject(txt));
    assertFalse(myIndex.getInfoForFile(java).isInProject(java));
  }

  public void testExcludeLibraryRootThatIsUnderContentRoot() {
    VirtualFile myLibraryRoot = createChildDirectory(myContentRoot, "library");
    VirtualFile txt = createChildData(myLibraryRoot, "a.txt");
    VirtualFile java = createChildData(myLibraryRoot, "A.java");
    registerLibrary(myLibraryRoot, fileName -> fileName.equals(myLibraryRoot.getNameSequence()));

    assertInProject(txt);
    assertNotInLibrarySources(txt, myModule);
    assertInProject(java);
    assertNotInLibrarySources(java, myModule);
    assertIndexableContent(Arrays.asList(txt, java), null);
  }

  private void addExcludePattern(@NotNull String pattern) {
    ModuleRootModificationUtil.updateModel(myModule,
                                           model -> MarkRootActionBase.findContentEntry(model, myContentRoot).addExcludePattern(pattern));
  }

  private void registerLibrary(@NotNull VirtualFile root, @Nullable Condition<CharSequence> excludePattern) {
    WriteAction.run(() -> ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(
      () -> PlatformTestUtil.registerExtension(AdditionalLibraryRootsProvider.EP_NAME, new AdditionalLibraryRootsProvider() {
        @NotNull
        @Override
        public Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
          return myProject == project ? Collections.singletonList(
            SyntheticLibrary.newImmutableLibrary(Collections.singleton(root), Collections.emptySet(), excludePattern)
          ) : Collections.emptyList();
        }
      }, getTestRootDisposable()), false, true));
  }
}
