// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class JarInfo {
  private final List<Pair<String, Object>> myContent;
  private final DestinationInfo myDestination;

  public JarInfo(@NotNull DestinationInfo destination) {
    myDestination = destination;
    myContent = new ArrayList<>();
  }

  public void addContent(String pathInJar, ArtifactRootDescriptor descriptor) {
    myContent.add(Pair.create(pathInJar, descriptor));
  }

  public void addJar(String pathInJar, JarInfo jarInfo) {
    myContent.add(Pair.create(pathInJar, jarInfo));
  }

  public List<Pair<String, Object>> getContent() {
    return myContent;
  }

  public DestinationInfo getDestination() {
    return myDestination;
  }

  public String getPresentableDestination() {
    return myDestination.getOutputPath();
  }
}
