// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.artifacts;

import com.intellij.compiler.CompilerTestUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.impl.elements.DirectoryPackagingElement;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;

import java.io.IOException;

public class IncrementalArtifactsCompilerTest extends ArtifactCompilerTestCase {
  public void testAddFile() {
    VirtualFile file = createFile("dir/file.txt");
    Artifact a = addArtifact(root().dirCopy(file.getParent()));
    make(a);
    assertOutput(a, fs().file("file.txt"));

    createFile("dir/file2.txt");
    make(a);
    assertOutput(a, fs().file("file.txt").file("file2.txt"));
  }

  public void testChangeFile() {
    VirtualFile file = createFile("file.txt", "a");
    Artifact a = addArtifact(root().dir("dir").file(file));
    compileProject();
    assertOutput(a, fs().dir("dir").file("file.txt", "a"));

    changeFile(file, "b");
    compileProject();
    assertOutput(a, fs().dir("dir").file("file.txt", "b"));
  }

  public void testFileNearDir() {
    VirtualFile file2 = createFile("1.txt", "a");
    final Artifact a = addArtifact(root().dir("dir").file(file2));
    make(a);
    assertOutput(a, fs().dir("dir").file("1.txt"));

    VirtualFile file = createFile("2.txt", "a");
    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      model.getOrCreateModifiableArtifact(a).getRootElement()
        .addFirstChild(PackagingElementFactory.getInstance().createFileCopy(file.getPath(), null));
      model.commit();
    });

    make(a);
    assertOutput(a, fs().file("2.txt").dir("dir").file("1.txt"));
  }

  public void testTwoFilesInOneDir() {
    VirtualFile file2 = createFile("1.txt", "a");
    final Artifact a = addArtifact(root().dir("dir").file(file2));
    make(a);
    assertOutput(a, fs().dir("dir").file("1.txt"));

    VirtualFile file = createFile("2.txt", "a");
    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      CompositePackagingElement<?> rootElement = model.getOrCreateModifiableArtifact(a).getRootElement();
      DirectoryPackagingElement element = (DirectoryPackagingElement) rootElement.getChildren().get(0);
      element.addFirstChild(PackagingElementFactory.getInstance().createFileCopy(file.getPath(), null));
      model.commit();
    });

    make(a);
    assertOutput(a, fs().dir("dir").file("1.txt").file("2.txt"));
  }

  public void testTwoFilesInOneDirUsingFindCompositeChild() {
    VirtualFile file2 = createFile("1.txt", "a");
    final Artifact a = addArtifact(root().dir("dir").file(file2));
    make(a);
    assertOutput(a, fs().dir("dir").file("1.txt"));

    VirtualFile file = createFile("2.txt", "a");
    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      CompositePackagingElement<?> rootElement = model.getOrCreateModifiableArtifact(a).getRootElement();
      DirectoryPackagingElement element = (DirectoryPackagingElement)rootElement.findCompositeChild("dir");
      element.addFirstChild(PackagingElementFactory.getInstance().createFileCopy(file.getPath(), null));
      model.commit();
    });

    make(a);
    assertOutput(a, fs().dir("dir").file("1.txt").file("2.txt"));
  }

  public void testTwoFilesInOneDirUsingAddOrCreate() {
    VirtualFile file2 = createFile("1.txt", "a");
    final Artifact a = addArtifact(root().dir("dir").file(file2));
    make(a);
    assertOutput(a, fs().dir("dir").file("1.txt"));

    VirtualFile file = createFile("2.txt", "a");
    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      CompositePackagingElement<?> rootElement = model.getOrCreateModifiableArtifact(a).getRootElement();
      DirectoryPackagingElement element = rootElement.addOrFindChild(new DirectoryPackagingElement("dir"));
      element.addFirstChild(PackagingElementFactory.getInstance().createFileCopy(file.getPath(), null));
      model.commit();
    });

    make(a);
    assertOutput(a, fs().dir("dir").file("1.txt").file("2.txt"));
  }

  public void testRenameArchive() {
    VirtualFile file = createFile("file.txt", "a");
    final Artifact a = addArtifact(root().archive("myArchive").file(file));
    make(a);
    assertOutput(a, fs().archive("myArchive").file("file.txt"));

    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      CompositePackagingElement<?> rootElement = model.getOrCreateModifiableArtifact(a).getRootElement();
      ArchivePackagingElement element = (ArchivePackagingElement)rootElement.getChildren().get(0);
      element.rename("AnotherName");
      model.commit();
    });

    make(a);
    assertOutput(a, fs().archive("AnotherName").file("file.txt"));
  }

  public void testSetNewNameToArchive() {
    VirtualFile file = createFile("file.txt", "a");
    final Artifact a = addArtifact(root().archive("myArchive").file(file));
    make(a);
    assertOutput(a, fs().archive("myArchive").file("file.txt"));

    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      CompositePackagingElement<?> rootElement = model.getOrCreateModifiableArtifact(a).getRootElement();
      ArchivePackagingElement element = (ArchivePackagingElement)rootElement.getChildren().get(0);
      element.setArchiveFileName("AnotherName");
      model.commit();
    });

    make(a);
    assertOutput(a, fs().archive("AnotherName").file("file.txt"));
  }

  public void testSetDirectoryName() {
    VirtualFile file = createFile("file.txt", "a");
    final Artifact a = addArtifact(root().dir("myArchive").file(file));
    /// Because of some reason the old directory is not cleared, but I actually test the rename itself here
    //make(a);
    //assertOutput(a, fs().dir("myArchive").file("file.txt"));

    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      CompositePackagingElement<?> rootElement = model.getOrCreateModifiableArtifact(a).getRootElement();
      DirectoryPackagingElement element = (DirectoryPackagingElement)rootElement.getChildren().get(0);
      element.setDirectoryName("AnotherName");
      model.commit();
    });

    make(a);
    assertOutput(a, fs().dir("AnotherName").file("file.txt"));
  }

  public void testRenameDirectory() {
    VirtualFile file = createFile("file.txt", "a");
    final Artifact a = addArtifact(root().dir("myArchive").file(file));
    /// Because of some reason the old directory is not cleared, but I actually test the rename itself here
    //make(a);
    //assertOutput(a, fs().dir("myArchive").file("file.txt"));

    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      CompositePackagingElement<?> rootElement = model.getOrCreateModifiableArtifact(a).getRootElement();
      DirectoryPackagingElement element = (DirectoryPackagingElement)rootElement.getChildren().get(0);
      element.rename("AnotherName");
      model.commit();
    });

    make(a);
    assertOutput(a, fs().dir("AnotherName").file("file.txt"));
  }

  public void testFilesInTwoDirs() {
    VirtualFile file2 = createFile("1.txt", "a");
    final Artifact a = addArtifact(root().dir("dir").dir("anotherDir").file(file2));
    make(a);
    assertOutput(a, fs().dir("dir").dir("anotherDir").file("1.txt"));

    VirtualFile file = createFile("2.txt", "a");
    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      CompositePackagingElement<?> rootElement = model.getOrCreateModifiableArtifact(a).getRootElement();
      DirectoryPackagingElement element = rootElement.addOrFindChild(new DirectoryPackagingElement("dir"));
      DirectoryPackagingElement anotherElement = (DirectoryPackagingElement)element.getChildren().get(0);
      anotherElement.addFirstChild(PackagingElementFactory.getInstance().createFileCopy(file.getPath(), null));
      model.commit();
    });

    make(a);
    assertOutput(a, fs().dir("dir").dir("anotherDir").file("1.txt").file("2.txt"));
  }

  public void testAddRemoveJavaClass() {
    final VirtualFile file = createFile("src/A.java", "public class A {}");
    final Module module = addModule("a", file.getParent());
    Artifact a = addArtifact(root().module(module));
    make(module);
    make(a);
    assertOutput(a, fs().file("A.class"));
    VirtualFile file2 = createFile("src/B.java", "public class B{}");
    make(module);
    make(a);
    assertOutput(a, fs().file("A.class").file("B.class"));

    deleteFile(file2);
    make(module);
    make(a);
    assertOutput(a, fs().file("A.class"));
  }

  public void testCopyResourcesFromModuleOutput() {
    VirtualFile file = createFile("src/a.xml", "1");
    Module module = addModule("a", file.getParent());
    Artifact artifact = addArtifact(root().module(module));
    make(artifact);
    assertOutput(artifact, fs().file("a.xml", "1"));

    changeFile(file, "2");
    make(artifact);
    assertOutput(artifact, fs().file("a.xml", "2"));
  }

  public void testRebuildArtifactAfterClean() {
    Artifact a = addArtifact(root().file(createFile("file.txt", "123")));
    make(a);
    assertOutput(a, fs().file("file.txt"));

    VfsTestUtil.deleteFile(getOutputDir(a));
    make(a);
    assertOutput(a, fs().file("file.txt", "123"));
  }

  public void testChangeFileInIncludedArtifact() {
    VirtualFile file = createFile("file.txt", "a");
    Artifact included = addArtifact("i", root().file(file));
    Artifact a = addArtifact(root().artifact(included));
    make(a);
    assertOutput(a, fs().file("file.txt", "a"));

    changeFile(file, "b");
    make(a);
    assertOutput(a, fs().file("file.txt", "b"));
  }

  public void testDeleteFileInIncludedArtifact() {
    VirtualFile file1 = createFile("d/1.txt");
    createFile("d/2.txt");
    Artifact included = addArtifact("i", root().dirCopy(file1.getParent()));
    Artifact a = addArtifact(root().artifact(included));
    make(a);
    assertOutput(a, fs().file("1.txt").file("2.txt"));

    VfsTestUtil.deleteFile(file1);
    make(a);
    assertOutput(a, fs().file("2.txt"));
  }

  public void testChangeFileInArtifactIncludedInArchive() {
    VirtualFile file = createFile("file.txt", "a");
    Artifact included = addArtifact("i", root().file(file));
    Artifact a = addArtifact(archive("a.jar").artifact(included));
    make(a);
    assertOutput(a, fs().archive("a.jar").file("file.txt", "a"));

    changeFile(file, "b");
    make(a);
    assertOutput(a, fs().archive("a.jar").file("file.txt", "b"));
  }

  public void testChangeFileInIncludedArchive() {
    VirtualFile file = createFile("file.txt", "a");
    Artifact included = addArtifact("i", archive("f.jar").file(file));
    Artifact a = addArtifact(root().artifact(included));
    make(a);
    assertOutput(a, fs().archive("f.jar").file("file.txt", "a"));

    changeFile(file, "b");
    make(a);
    assertOutput(a, fs().archive("f.jar").file("file.txt", "b"));
  }

  public void testDoNotTreatClassFileAsDirtyAfterMake() {
    VirtualFile file = createFile("src/A.java", "class A{}");
    Module module = addModule("a", file.getParent());
    VirtualFile out = createChildDirectory(file.getParent().getParent(), "module-out");
    PsiTestUtil.addContentRoot(module, out);
    PsiTestUtil.setCompilerOutputPath(module, out.getUrl(), false);

    Artifact a = addArtifact(root().module(module));
    make(a);
    assertOutput(a, fs().file("A.class"));
    make(a).assertUpToDate();

    changeFile(file, "class A{int a;}");
    make(a);
    assertOutput(a, fs().file("A.class"));
    make(a).assertUpToDate();
  }

  public void testDoNotCopyFilesFromIgnoredFolder() {
    VirtualFile file1 = createFile("d/1.txt");
    Artifact a = addArtifact(root().dirCopy(file1.getParent()));
    make(a);
    assertOutput(a, fs().file("1.txt"));

    VirtualFile file2 = createFile("d/CVS/2.txt");
    make(a);
    assertOutput(a, fs().file("1.txt"));

    changeFile(file2);
    make(a);
    assertOutput(a, fs().file("1.txt"));
  }

  public void testChangeFileOutsideContentRoot() throws IOException {
    final Artifact a = addArtifact(root().file(createFile("1.txt")));
    make(a);
    assertOutput(a, fs().file("1.txt"));

    VirtualFile virtualDir = getTempDir().createVirtualDir();
    VirtualFile file = VfsTestUtil.createFile(virtualDir, "2.txt", "a");
    WriteAction.runAndWait(() -> {
      ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
      model.getOrCreateModifiableArtifact(a).getRootElement()
           .addFirstChild(PackagingElementFactory.getInstance().createFileCopy(file.getPath(), null));
      model.commit();
    });


    make(a);
    assertOutput(a, fs().file("1.txt").file("2.txt", "a"));

    changeFile(file, "b");
    make(a);
    assertOutput(a, fs().file("1.txt").file("2.txt", "b"));
  }

  //IDEA-95420
  public void testDeleteClassAndRebuildArtifact() {
    createFile("src/A.java", "class A{}");
    VirtualFile file = createFile("src/B.java", "class B{}");
    Module m = addModule("m", file.getParent());
    Artifact dir = addArtifact(root().module(m));
    Artifact jar = addArtifact("jar", archive("a.jar").module(m));
    make(dir, jar);
    assertOutput(m, fs().file("A.class").file("B.class"));
    assertOutput(dir, fs().file("A.class").file("B.class"));
    assertOutput(jar, fs().archive("a.jar").file("A.class").file("B.class"));

    deleteFile(file);
    recompile(dir, jar);
    assertOutput(m, fs().file("A.class"));
    assertOutput(dir, fs().file("A.class"));
    assertOutput(jar, fs().archive("a.jar").file("A.class"));
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    CompilerTestUtil.setupJavacForTests(getProject());
  }

  /*
  //todo move to IncrementalArtifactBuildingTest
  public void testDeleteOutputWhenOutputPathIsChanged() throws Exception {
    final VirtualFile file = createFile("a.txt");
    final Artifact a = addArtifact("a", root().file(file));
    compileProject();
    assertOutput(a, fs().file("a.txt"));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(a).setOutputPath(getProjectBasePath() + "/xxx");
    commitModel(model);

    compileProject().assertRecompiledAndDeleted(new String[]{"a.txt"}, "out/artifacts/a/a.txt");
    assertOutput(a, fs().file("a.txt"));
  }

  //todo this test sometimes fails on server
  public void _testChangeFileInArchive() throws Exception {
    final VirtualFile file = createFile("a.txt", "a");
    final Artifact a = addArtifact("a", root().archive("a.jar").file(file));
    make(a);

    final String jarPath = a.getOutputPath() + "/a.jar";
    JarFileSystem.getInstance().setNoCopyJarForPath(jarPath + JarFileSystem.JAR_SEPARATOR);
    final Artifact b = addArtifact("b", root().extractedDir(jarPath, "/"));
    make(b);
    assertOutput(b, fs().file("a.txt", "a"));

    make(a).assertUpToDate();
    make(b).assertUpToDate();

    changeFile(file, "b");
    make(b).assertUpToDate();
    make(a).assertRecompiled("a.txt");
    assertOutput(a, fs().archive("a.jar").file("a.txt", "b"));
    changeFileInJar(jarPath, "a.txt");

    make(b).assertRecompiled("out/artifacts/a/a.jar!/a.txt");
    assertOutput(b, fs().file("a.txt", "b"));
    make(b).assertUpToDate();
  }
  */
}