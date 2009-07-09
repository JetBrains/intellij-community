package com.intellij.compiler.artifacts;

import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;

/**
 * @author nik
 */
public class ArtifactPointerManagerTest extends ArtifactsTestCase {
  public void testCreateFromName() throws Exception {
    final Artifact artifact = addArtifact("art");
    final ArtifactPointer pointer = getPointerManager().create("art");
    assertSame(artifact, pointer.getArtifact());
    assertSame(artifact, pointer.findArtifact(getArtifactManager()));
    assertSame(pointer, getPointerManager().create(artifact));
    assertEquals("art", pointer.getName());
  }

  public void testCreateFromArtifact() throws Exception {
    final Artifact artifact = addArtifact("aaa");
    final ArtifactPointer pointer = getPointerManager().create(artifact);
    assertSame(artifact, pointer.getArtifact());
    assertEquals("aaa", pointer.getName());
    assertSame(pointer, getPointerManager().create("aaa"));
  }

  public void testRenameArtifact() throws Exception {
    final Artifact artifact = addArtifact("art1");
    final ArtifactPointer pointer = getPointerManager().create("art1");
    assertSame(artifact, pointer.getArtifact());
    assertEquals("art1", pointer.getName());

    final Artifact newArtifact = rename(artifact, "art2");
    assertSame(newArtifact, pointer.getArtifact());
    assertEquals("art2", pointer.getName());
    assertSame(pointer, getPointerManager().create("art2"));
  }

  public void testCreateArtifactAfterPointer() throws Exception {
    final ArtifactPointer pointer = getPointerManager().create("xxx");
    assertNull(pointer.getArtifact());

    final Artifact artifact = addArtifact("xxx");
    assertSame(artifact, pointer.getArtifact());
    assertSame(pointer, getPointerManager().create("xxx"));
    assertSame(pointer, getPointerManager().create(artifact));
  }

  public void testDeleteArtifact() throws Exception {
    final Artifact artifact = addArtifact("abc");
    final ArtifactPointer pointer = getPointerManager().create(artifact);
    assertSame(artifact, pointer.getArtifact());

    deleteArtifact(artifact);
    assertNull(pointer.getArtifact());
  }

  private ArtifactPointerManager getPointerManager() {
    return ArtifactPointerManager.getInstance(myProject);
  }
}
