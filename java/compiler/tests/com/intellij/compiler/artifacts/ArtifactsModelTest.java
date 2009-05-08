package com.intellij.compiler.artifacts;

import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactsModelTest extends ArtifactsTestCase {

  public void testAddArtifact() throws Exception {
    assertEmpty(getArtifacts());

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    final ModifiableArtifact artifact = model.addArtifact("new art", PlainArtifactType.getInstance());
    artifact.setBuildOnMake(true);
    artifact.setOutputPath("/myout");
    artifact.setName("xxx");

    final MyArtifactListener listener = subscribe();
    assertEmpty(getArtifacts());
    model.commit();
    final Artifact newArt = assertOneElement(getArtifacts());
    assertEquals("xxx", newArt.getName());
    assertTrue(newArt.isBuildOnMake());
    assertEquals("/myout", newArt.getOutputPath());
    assertEquals("added:xxx;", listener.clearMessages());
  }

  public void testRemoveArtifact() throws Exception {
    Artifact artifact = addArtifact("aaa");
    assertSame(artifact, assertOneElement(getArtifacts()));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.removeArtifact(artifact);
    final MyArtifactListener listener = subscribe();
    model.commit();

    assertEmpty(getArtifacts());
    assertEquals("removed:aaa;", listener.clearMessages());
  }

  public void testChangeAndRemoveArtifact() throws Exception {
    doTestChangeAndRemove(false);
  }

  public void testChangeAndRemoveOriginalArtifact() throws Exception {
    doTestChangeAndRemove(true);
  }

  private void doTestChangeAndRemove(final boolean removeOriginal) {
    final Artifact artifact = addArtifact("aaa");
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    final ModifiableArtifact modifiable = model.getOrCreateModifiableArtifact(artifact);
    modifiable.setName("bbb");
    model.removeArtifact(removeOriginal ? artifact : modifiable);


    final MyArtifactListener listener = subscribe();
    model.commit();
    assertEmpty(getArtifacts());
    assertEquals("removed:aaa;", listener.clearMessages());
  }

  public void testChangeArtifact() throws Exception {
    final Artifact artifact = addArtifact("xxx");
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    assertFalse(model.isModified());
    assertSame(artifact, model.getArtifactByOriginal(artifact));

    final ModifiableArtifact modifiable = model.getOrCreateModifiableArtifact(artifact);
    assertNotNull(modifiable);
    assertEquals("xxx", modifiable.getName());
    assertNotSame(modifiable, artifact);

    modifiable.setOutputPath("/aaa");
    modifiable.setName("qqq");
    assertNull(artifact.getOutputPath());
    assertEquals("xxx", artifact.getName());

    assertSame(modifiable, model.getOrCreateModifiableArtifact(artifact));
    assertSame(modifiable, model.getOrCreateModifiableArtifact(modifiable));
    assertSame(modifiable, assertOneElement(model.getArtifacts()));
    assertSame(modifiable, model.findArtifact("qqq"));
    assertNull(model.findArtifact("xxx"));
    assertSame(modifiable, model.getArtifactByOriginal(artifact));
    assertTrue(model.isModified());

    final MyArtifactListener listener = subscribe();
    model.commit();
    assertEquals("changed:xxx->qqq;", listener.clearMessages());
    final Artifact newArtifact = assertOneElement(getArtifacts());
    assertEquals("qqq", newArtifact.getName());
    assertEquals("/aaa", newArtifact.getOutputPath());
  }


  private Artifact[] getArtifacts() {
    return getArtifactManager().getArtifacts();
  }

  private Artifact addArtifact(String name) {
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    final ModifiableArtifact artifact = model.addArtifact(name, PlainArtifactType.getInstance());
    model.commit();
    return artifact;
  }

  private MyArtifactListener subscribe() {
    final MyArtifactListener listener = new MyArtifactListener();
    myProject.getMessageBus().connect().subscribe(ArtifactManager.TOPIC, listener);
    return listener;
  }

  private static class MyArtifactListener extends ArtifactAdapter {
    private StringBuilder myMessages = new StringBuilder();

    @Override
    public void artifactAdded(@NotNull Artifact artifact) {
      myMessages.append("added:").append(artifact.getName()).append(";");
    }

    @Override
    public void artifactRemoved(@NotNull Artifact artifact) {
      myMessages.append("removed:").append(artifact.getName()).append(";");
    }

    @Override
    public void artifactChanged(@NotNull Artifact original, Artifact modified) {
      myMessages.append("changed:").append(original.getName()).append("->").append(modified.getName()).append(";");
    }

    public String clearMessages() {
      final String messages = myMessages.toString();
      myMessages.setLength(0);
      return messages;
    }
  }
}
