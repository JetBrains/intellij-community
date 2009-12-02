package com.intellij.compiler.artifacts.ui;

import com.intellij.compiler.artifacts.ArtifactsTestUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.actions.ExtractArtifactAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestInputDialog;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;

/**
 * @author nik
 */
public class ExtractArtifactActionTest extends ArtifactEditorActionTestCase {

  public void testSimple() throws Exception {
    createEditor(addArtifact(root().file(createFile("a.txt"))));
    assertDisabled();

    selectNode("a.txt");
    extract("new");
    assertLayout("<root>\n" +
                 " artifact:new");

    applyChanges();
    assertLayout(ArtifactsTestUtil.findArtifact(myProject, "new"), "<root>\n" +
                                                                   " file:" + getProjectBasePath() + "/a.txt");
  }

  public void testDisabledForJarFromLib() throws Exception {
    final VirtualFile jar = getJDomJar();
    createEditor(addArtifact(root().lib(addProjectLibrary(null, "dom", jar))), true);

    selectNode("jdom.jar");
    assertWillNotBePerformed();
  }

  public void testDirectoryFromIncludedArtifact() throws Exception {
    final Artifact included = addArtifact("included", root().dir("dir").file(createFile("a.txt")));
    createEditor(addArtifact(root()
                              .artifact(included)
                              .dir("dir")
                                .file(createFile("b.txt"))), true);
    selectNode("dir");
    assertDisabled();

    selectNode("dir/a.txt");
    assertWillNotBePerformed();

    selectNode("dir/b.txt");
    extract("new");
    assertLayout("<root>\n" +
                 " artifact:included\n" +
                 " dir/\n" +
                 "  artifact:new");
    applyChanges();
    assertLayout(ArtifactsTestUtil.findArtifact(myProject, "new"), "<root>\n" +
                                                                   " file:" + getProjectBasePath() + "/b.txt");
  }

  private void extract(final String name) {
    final Ref<Boolean> shown = Ref.create(false);
    final TestInputDialog old = Messages.setTestInputDialog(new TestInputDialog() {
      public String show(String message) {
        shown.set(true);
        return name;
      }
    });
    try {
      perform();
    }
    finally {
      Messages.setTestInputDialog(old);
    }
    assertTrue(shown.get());
  }

  @Override
  protected AnAction createAction(final ArtifactEditorEx artifactEditor) {
    return new ExtractArtifactAction(artifactEditor);
  }
}
