// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.artifacts;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.PackagingElementFactory;

import java.io.IOException;

public class UpdateArtifactsAfterRenameTest extends PackagingElementsTestCase {
  public void testRenameFile() {
    final VirtualFile file = createFile("a.txt");
    final Artifact artifact = addArtifact(root().dir("xxx").file(file));
    renameFile(file, "b.txt");
    assertLayout(artifact, "<root>\n" +
                           " xxx/\n" +
                           "  file:" + getProjectBasePath() + "/b.txt");
  }

  public void testRenameDirectory() {
    final VirtualFile dir = createFile("dir/a.txt").getParent();
    final Artifact artifact = addArtifact(root().dirCopy(dir));
    renameFile(dir, "xxx");
    assertLayout(artifact, "<root>\n" +
                           " dir:" + getProjectBasePath() + "/xxx");
  }

  public void testRenameDirectoryForTwoArtifacts() {
    final VirtualFile dir = createFile("dir/a.txt").getParent();
    final Artifact artifact = addArtifact(root().dirCopy(dir));
    final Artifact artifact2 = addArtifact(root().dirCopy(dir));
    renameFile(dir, "xxx");
    assertLayout(artifact, "<root>\n" +
                           " dir:" + getProjectBasePath() + "/xxx");
    assertLayout(artifact2, "<root>\n" +
                            " dir:" + getProjectBasePath() + "/xxx");
  }

  public void testRenameDirectoryForTwoArtifactsAndSecondRename() {
    final VirtualFile dir = createFile("dir/a.txt").getParent();
    final Artifact artifact = addArtifact(root().dirCopy(dir));
    final Artifact artifact2 = addArtifact(root().dirCopy(dir));
    renameFile(dir, "xxx");
    assertLayout(artifact, "<root>\n" +
                           " dir:" + getProjectBasePath() + "/xxx");
    assertLayout(artifact2, "<root>\n" +
                            " dir:" + getProjectBasePath() + "/xxx");
    renameFile(dir, "yyy");
    assertLayout(artifact, "<root>\n" +
                           " dir:" + getProjectBasePath() + "/yyy");
    assertLayout(artifact2, "<root>\n" +
                            " dir:" + getProjectBasePath() + "/yyy");
  }

  public void testRenameDirectoryForTwoArtifactsAndAddingRoots() {
    final VirtualFile dir = createFile("dir/a.txt").getParent();
    final VirtualFile dir2 = createFile("dir2/b.txt").getParent();
    final Artifact artifact = addArtifact(root().dirCopy(dir));
    renameFile(dir, "xxx");
    assertLayout(artifact, "<root>\n" +
                           " dir:" + getProjectBasePath() + "/xxx");

    final PackagingElementFactory packagingElementFactory = PackagingElementFactory.getInstance();
    ModifiableArtifactModel model = ArtifactManager.getInstance(myProject).createModifiableModel();
    ModifiableArtifact mutableArtifact = model.getOrCreateModifiableArtifact(artifact);
    mutableArtifact.getRootElement().addFirstChild(packagingElementFactory.createDirectoryCopyWithParentDirectories(dir2.getPath(), "/"));
    WriteAction.run(() -> {
      model.commit();
    });

    assertLayout(artifact, "<root>\n" +
                           " dir:" + getProjectBasePath() + "/dir2\n" +
                           " dir:" + getProjectBasePath() + "/xxx"
    );

    renameFile(dir2, "yyy");
    assertLayout(artifact, "<root>\n" +
                           " dir:" + getProjectBasePath() + "/yyy\n" +
                           " dir:" + getProjectBasePath() + "/xxx"
    );
  }

  public void testRenameDirectoryForRemovedArtifactElements() {
    final VirtualFile dir = createFile("dir/a.txt").getParent();
    final Artifact artifact = addArtifact(root().dirCopy(dir));
    renameFile(dir, "xxx");
    assertLayout(artifact, "<root>\n" +
                           " dir:" + getProjectBasePath() + "/xxx");

    final PackagingElementFactory packagingElementFactory = PackagingElementFactory.getInstance();
    ModifiableArtifactModel model = ArtifactManager.getInstance(myProject).createModifiableModel();
    ModifiableArtifact mutableArtifact = model.getOrCreateModifiableArtifact(artifact);
    mutableArtifact.getRootElement().removeChild(mutableArtifact.getRootElement().getChildren().get(0));
    WriteAction.run(() -> {
      model.commit();
    });

    assertLayout(artifact, "<root>");

    renameFile(dir, "zzz");
    assertLayout(artifact, "<root>");
  }

  public void testMoveFile() {
    final VirtualFile file = createFile("a/xxx.txt");
    final Artifact artifact = addArtifact(root().file(file));
    VirtualFile baseDir = getOrCreateProjectBaseDir();
    moveFile(file, createChildDirectory(baseDir, "b"));
    assertLayout(artifact, "<root>\n" +
                           " file:" + getProjectBasePath() + "/b/xxx.txt");
  }

  public void testRenameParentDir() {
    final VirtualFile file = createFile("x/a.txt");
    final Artifact artifact = addArtifact(root().file(file));
    renameFile(file.getParent(), "y");
    assertLayout(artifact, "<root>\n" +
                           " file:" + getProjectBasePath() + "/y/a.txt");
  }

  public void testMoveParentDir() {
    final VirtualFile file = createFile("a/b/c.txt");
    final Artifact artifact = addArtifact(root().file(file));
    VirtualFile baseDir = getOrCreateProjectBaseDir();
    moveFile(file.getParent(), createChildDirectory(baseDir, "d"));
    assertLayout(artifact, "<root>\n" +
                           " file:" + getProjectBasePath() + "/d/b/c.txt");
  }

  public void testRenameArtifact() {
    final Artifact xxx = addArtifact("xxx");
    final Artifact artifact = addArtifact(root().artifact(xxx));
    rename(xxx, "yyy");
    assertLayout(artifact, "<root>\n" +
                           " artifact:yyy");
  }

  public void testRenameModule() throws ModuleWithNameAlreadyExists {
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final Module module = WriteAction.computeAndWait(() -> {
      Module res = moduleManager.newModule(getProjectBasePath() + "/myModule.iml", JavaModuleType.getModuleType().getId());
      return res;
    });
    final Artifact artifact = addArtifact(root().module(module).moduleSource(module));

    assertLayout(artifact, """
      <root>
       module:myModule
       module sources:myModule""");
    WriteAction.runAndWait(() -> {
      final ModifiableModuleModel model = moduleManager.getModifiableModel();
      model.renameModule(module, "newName");
      model.commit();
    });

    assertLayout(artifact, """
      <root>
       module:newName
       module sources:newName""");

    moduleManager.disposeModule(module);

    assertLayout(artifact, """
      <root>
       module:newName
       module sources:newName""");
  }

  private void moveFile(final VirtualFile file, final VirtualFile newParent) {
    try {
      WriteAction.runAndWait(() -> file.move(this, newParent));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
