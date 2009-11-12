package com.intellij.compiler.artifacts;

import com.intellij.compiler.CompilerTestUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.testFramework.PsiTestUtil;

/**
 * @author nik
 */
public class ArtifactCompileScopeTest extends ArtifactCompilerTestCase {
  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    CompilerTestUtil.setupJavacForTests(myProject);
  }

  public void testMakeModule() throws Exception {
    final VirtualFile file1 = createFile("src1/A.java", "public class A {}");
    final Module module1 = addModule("module1", file1.getParent());
    final VirtualFile file2 = createFile("src2/B.java", "public class B {}");
    final Module module2 = addModule("module2", file2.getParent());
    CompilerTestUtil.scanSourceRootsToRecompile(myProject);

    final Artifact artifact = addArtifact(root().module(module1));

    compile(module1);
    assertNoOutput(artifact);

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(artifact).setBuildOnMake(true);
    commitModel(model);                 

    compile(module2);
    assertNoOutput(artifact);

    compile(module1);
    assertOutput(artifact, fs().file("A.class"));
  }

  private Module addModule(final String moduleName, final VirtualFile sourceRoot) {
    return new WriteAction<Module>() {
      protected void run(final Result<Module> result) {
        final Module module = createModule(moduleName);
        PsiTestUtil.addSourceContentToRoots(module, sourceRoot);
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        model.setSdk(getTestProjectJdk());
        model.commit();
        result.setResult(module);
      }
    }.execute().getResultObject();
  }

}
