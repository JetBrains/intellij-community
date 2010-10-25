package com.intellij.compiler.artifacts;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;

import java.io.IOException;

/**
 * @author nik
 */
public class UpdateArtifactsAfterRenameTest extends PackagingElementsTestCase {

  public void testRenameFile() throws Exception {
    final VirtualFile file = createFile("a.txt");
    final Artifact artifact = addArtifact(root().dir("xxx").file(file));
    renameFile(file, "b.txt");
    assertLayout(artifact, "<root>\n" +
                           " xxx/\n" +
                           "  file:" + getProjectBasePath() + "/b.txt");
  }

  public void testRenameDirectory() throws Exception {
    final VirtualFile dir = createFile("dir/a.txt").getParent();
    final Artifact artifact = addArtifact(root().dirCopy(dir));
    renameFile(dir, "xxx");
    assertLayout(artifact, "<root>\n" +
                           " dir:" + getProjectBasePath() + "/xxx");
  }

  public void testMoveFile() throws Exception {
    final VirtualFile file = createFile("a/xxx.txt");
    final Artifact artifact = addArtifact(root().file(file));
    moveFile(file, getBaseDir().createChildDirectory(this, "b"));
    assertLayout(artifact, "<root>\n" +
                           " file:" + getProjectBasePath() + "/b/xxx.txt");
  }

  public void testRenameParentDir() throws Exception {
    final VirtualFile file = createFile("x/a.txt");
    final Artifact artifact = addArtifact(root().file(file));
    renameFile(file.getParent(), "y");
    assertLayout(artifact, "<root>\n" +
                           " file:" + getProjectBasePath() + "/y/a.txt");
  }

  public void testMoveParentDir() throws Exception {
    final VirtualFile file = createFile("a/b/c.txt");
    final Artifact artifact = addArtifact(root().file(file));
    moveFile(file.getParent(), getBaseDir().createChildDirectory(this, "d"));
    assertLayout(artifact, "<root>\n" +
                           " file:" + getProjectBasePath() + "/d/b/c.txt");
  }

  public void testRenameArtifact() throws Exception {
    final Artifact xxx = addArtifact("xxx");
    final Artifact artifact = addArtifact(root().artifact(xxx));
    rename(xxx, "yyy");
    assertLayout(artifact, "<root>\n" +
                           " artifact:yyy");
  }

  public void testRenameModule() throws Exception {
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final Module module = new WriteAction<Module>() {
      @Override
      protected void run(Result<Module> result) throws Throwable {
        Module res = moduleManager.newModule(getProjectBasePath() + "/myModule.iml", StdModuleTypes.JAVA);
        result.setResult(res);
      }
    }.execute().getResultObject();
    final Artifact artifact = addArtifact(root().module(module));

    assertLayout(artifact, "<root>\n" +
                           " module:myModule");
    new WriteAction() {
      @Override
      protected void run(final Result result) throws ModuleWithNameAlreadyExists {
        final ModifiableModuleModel model = moduleManager.getModifiableModel();
        model.renameModule(module, "newName");
        model.commit();
      }
    }.execute();

    assertLayout(artifact, "<root>\n" +
                           " module:newName");

    moduleManager.disposeModule(module);

    assertLayout(artifact, "<root>\n" +
                           " module:newName");
  }

  private void moveFile(final VirtualFile file, final VirtualFile newParent) {
    new WriteAction() {
      @Override
      protected void run(final Result result) {
        try {
          file.move(this, newParent);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
  }

}
