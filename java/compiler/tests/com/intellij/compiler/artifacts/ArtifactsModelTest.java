// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.artifacts;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.impl.elements.ArtifactRootElementImpl;
import com.intellij.packaging.impl.elements.DirectoryPackagingElement;
import com.intellij.packaging.impl.elements.LibraryPackagingElement;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotEquals;

public class ArtifactsModelTest extends ArtifactsTestCase {
  public void testAddArtifact() {
    assertEmpty(getArtifacts());

    final long count = getModificationCount();
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    final ModifiableArtifact artifact = model.addArtifact("new art", PlainArtifactType.getInstance());
    artifact.setBuildOnMake(true);
    artifact.setOutputPath("/myout");
    artifact.setName("xxx");

    final MyArtifactListener listener = subscribe();
    assertEmpty(getArtifacts());
    commit(model);
    final Artifact newArt = assertOneElement(getArtifacts());
    assertEquals("xxx", newArt.getName());
    assertTrue(newArt.isBuildOnMake());
    assertEquals("/myout", newArt.getOutputPath());
    assertEquals("added:xxx;", listener.clearMessages());
    assertTrue(getModificationCount() > count);
  }

  public void testAccessArtifactElementsThatAreUnderModification() {
    assertEmpty(getArtifacts());

    var artifact = addArtifact("MyArtifact", new ArtifactRootElementImpl());

    ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    ModifiableArtifact modifiableArtifact = model.getOrCreateModifiableArtifact(artifact);
    modifiableArtifact.getRootElement().getChildren();  // This is important to access prev root element
    modifiableArtifact.setRootElement(new ArtifactRootElementImpl());

    var found = model.getArtifactsByType(PlainArtifactType.getInstance()).stream().findFirst().get();
    CompositePackagingElement<?> root = found.getRootElement();

    root.getChildren();
  }

  public void testAccessArtifactElementsThatAreUnderModification2() {
    assertEmpty(getArtifacts());

    ArtifactRootElementImpl myRoot = new ArtifactRootElementImpl();
    myRoot.addFirstChild(new DirectoryPackagingElement("dir"));
    var artifact = addArtifact("MyArtifact", myRoot);

    ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    ModifiableArtifact modifiableArtifact = model.getOrCreateModifiableArtifact(artifact);
    ((CompositePackagingElement<?>)modifiableArtifact.getRootElement().getChildren().get(0)).getChildren();  // This is important to access prev root element
    modifiableArtifact.setRootElement(new ArtifactRootElementImpl());

    var found = model.getArtifactsByType(PlainArtifactType.getInstance()).stream().findFirst().get();
    CompositePackagingElement<?> root = found.getRootElement();

    ((CompositePackagingElement<?>)root.getChildren().get(0)).getChildren();
  }

  private long getModificationCount() {
    return getArtifactManager().getModificationTracker().getModificationCount();
  }

  public void testRemoveArtifact() {
    Artifact artifact = addArtifact("aaa");
    assertSame(artifact, assertOneElement(getArtifacts()));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.removeArtifact(artifact);
    final MyArtifactListener listener = subscribe();
    commit(model);

    assertEmpty(getArtifacts());
    assertEquals("removed:aaa;", listener.clearMessages());
  }

  public void testRemoveArtifactWithExistingModifiableArtifact() {
    Artifact artifact = addArtifact("aaa");
    assertSame(artifact, assertOneElement(getArtifacts()));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();

    // Just create an instance of modifiable artifact
    model.getOrCreateModifiableArtifact(artifact);

    model.removeArtifact(artifact);
    final MyArtifactListener listener = subscribe();
    commit(model);

    assertEmpty(getArtifacts());
    assertEquals("removed:aaa;", listener.clearMessages());
  }

  public void testChangeAndRemoveArtifact() {
    doTestChangeAndRemove(false);
  }

  public void testChangeAndRemoveOriginalArtifact() {
    doTestChangeAndRemove(true);
  }

  private void doTestChangeAndRemove(final boolean removeOriginal) {
    final Artifact artifact = addArtifact("aaa");
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    final ModifiableArtifact modifiable = model.getOrCreateModifiableArtifact(artifact);
    modifiable.setName("bbb");
    model.removeArtifact(removeOriginal ? artifact : modifiable);


    final MyArtifactListener listener = subscribe();
    commit(model);
    assertEmpty(getArtifacts());
    assertEquals("removed:aaa;", listener.clearMessages());
  }

  private static void commit(ModifiableArtifactModel model) {
    ApplicationManager.getApplication().runWriteAction(() -> model.commit());
  }

  public void testChangeArtifact() {
    Artifact artifact = addArtifact("xxx");

    long count = getModificationCount();
    ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    assertFalse(model.isModified());
    assertSame(artifact, model.getArtifactByOriginal(artifact));

    ModifiableArtifact modifiable = model.getOrCreateModifiableArtifact(artifact);
    assertNotNull(modifiable);
    assertEquals("xxx", modifiable.getName());
    assertNotSame(modifiable, artifact);

    modifiable.setOutputPath("/aaa");
    modifiable.setName("qqq");
    assertThat(artifact.getOutputPath()).isEqualTo(getProject().getBasePath() + "/out/artifacts/xxx");
    assertEquals("xxx", artifact.getName());

    assertSame(modifiable, model.getOrCreateModifiableArtifact(artifact));
    assertSame(modifiable, model.getOrCreateModifiableArtifact(modifiable));
    assertSame(modifiable, assertOneElement(model.getArtifacts()));
    assertSame(modifiable, model.findArtifact("qqq"));
    assertNull(model.findArtifact("xxx"));
    assertSame(modifiable, model.getArtifactByOriginal(artifact));
    assertTrue(model.isModified());

    MyArtifactListener listener = subscribe();
    commit(model);
    assertEquals("changed:xxx->qqq;", listener.clearMessages());
    Artifact newArtifact = assertOneElement(getArtifacts());
    assertEquals("qqq", newArtifact.getName());
    assertEquals("/aaa", newArtifact.getOutputPath());
    assertSame(newArtifact, artifact);
    assertTrue(getModificationCount() > count);
  }

  public void testReplaceArtifact() {
    // This test checks if the recreation of the artifact with the same name doesn't replace the new artifact with the new one
    Artifact artifact = addArtifact("aaa");
    assertSame(artifact, assertOneElement(getArtifacts()));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();

    // Just create a modifiable artifact
    model.getOrCreateModifiableArtifact(artifact);

    model.removeArtifact(artifact);

    ModifiableArtifact newModifiableArtifact = model.addArtifact("aaa", PlainArtifactType.getInstance());
    newModifiableArtifact.setBuildOnMake(true);
    commit(model);

    assertTrue(newModifiableArtifact.isBuildOnMake());
  }

  public void testReplaceArtifactWithCheckingModifiableArtifactData() {
    // This test checks if the recreation of the artifact with the same name doesn't replace the new artifact with the new one
    Artifact artifact = addArtifact("A");
    assertSame(artifact, assertOneElement(getArtifacts()));
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    // Just create a modifiable artifact
    ModifiableArtifact newArtifact = model.getOrCreateModifiableArtifact(artifact);
    model.removeArtifact(artifact);
    ModifiableArtifact newModifiableArtifact = model.addArtifact("A", PlainArtifactType.getInstance());
    newModifiableArtifact.setBuildOnMake(true);
    newModifiableArtifact.setOutputPath("B");
    commit(model);
    assertEquals("B", newModifiableArtifact.getOutputPath());
    assertTrue(newModifiableArtifact.isBuildOnMake());
    assertFalse(newArtifact.isBuildOnMake());
    assertNotEquals("B", newArtifact.getOutputPath());
  }

  public void  testLibraryElementHasPresentationWithoutStorage() {
    try {
      new LibraryPackagingElement("level", "libName", "name").createPresentation(
        new MockPackagingEditorContext(new MockArtifactsStructureConfigurableContext(), null));
    }
    catch (Exception e) {
      e.printStackTrace();
      Assert.fail("Exception is not expected");
    }
  }

  private Artifact[] getArtifacts() {
    return getArtifactManager().getArtifacts();
  }

  private MyArtifactListener subscribe() {
    final MyArtifactListener listener = new MyArtifactListener();
    myProject.getMessageBus().connect().subscribe(ArtifactManager.TOPIC, listener);
    return listener;
  }

  private static class MyArtifactListener implements ArtifactListener {
    private final StringBuilder myMessages = new StringBuilder();

    @Override
    public void artifactAdded(@NotNull Artifact artifact) {
      myMessages.append("added:").append(artifact.getName()).append(";");
    }

    @Override
    public void artifactRemoved(@NotNull Artifact artifact) {
      myMessages.append("removed:").append(artifact.getName()).append(";");
    }

    @Override
    public void artifactChanged(@NotNull Artifact artifact, @NotNull String oldName) {
      myMessages.append("changed:").append(oldName).append("->").append(artifact.getName()).append(";");
    }

    public String clearMessages() {
      final String messages = myMessages.toString();
      myMessages.setLength(0);
      return messages;
    }
  }
}
