package com.intellij.compiler.artifacts;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.impl.artifacts.ArtifactBySourceFileFinder;

import java.util.Collection;

/**
 * @author nik
 */
public class ArtifactBySourceFileFinderTest extends PackagingElementsTestCase {
  public void testAddRemoveDirectoryCopy() throws Exception {
    final VirtualFile file = createFile("a/b/c.txt");
    assertEmpty(findArtifacts(file));

    final Artifact a = addArtifact(root().dirCopy(file.getParent()));
    assertSame(a, assertOneElement(findArtifacts(file)));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(a).getRootElement().removeAllChildren();
    commitModel(model);

    assertEmpty(findArtifacts(file));
  }

  public void testAddRemoveArtifact() throws Exception {
    final VirtualFile file = createFile("a/b/c.txt");
    assertEmpty(findArtifacts(file));

    final Artifact a = addArtifact(root().file(file));
    assertSame(a, assertOneElement(findArtifacts(file)));

    deleteArtifact(a);

    assertEmpty(findArtifacts(file));
  }

  private Collection<? extends Artifact> findArtifacts(VirtualFile file) {
    return ArtifactBySourceFileFinder.getInstance(myProject).findArtifacts(file);
  }
}
