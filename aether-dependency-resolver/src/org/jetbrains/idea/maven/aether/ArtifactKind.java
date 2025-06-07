// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public enum ArtifactKind {
  ARTIFACT("", "jar"), SOURCES("sources", "jar"), JAVADOC("javadoc", "jar"),
  ANNOTATIONS("annotations", "zip"), AAR_ARTIFACT("", "aar"), POM("", "pom"),
  ALL("all", "jar"), HTTP("http", "jar"), DLL("", "dll"),
  ZIP("", "zip"), KLIB("", "klib");

  private final String myClassifier;
  private final String myExtension;

  ArtifactKind(String classifier, String extension) {
    myClassifier = classifier;
    myExtension = extension;
  }

  public @NotNull String getClassifier() {
    return myClassifier;
  }

  public @NotNull String getExtension() {
    return myExtension;
  }

  public static ArtifactKind find(String classifier, String extension) {
    for (ArtifactKind kind : ArtifactKind.values()) {
      if (kind.getClassifier().equals(classifier) && kind.getExtension().equals(extension)) {
        return kind;
      }
    }
    return null;
  }

  public static EnumSet<ArtifactKind> kindsOf(boolean sources, boolean javadoc, String... artifactPackaging) {
    EnumSet<ArtifactKind> result = EnumSet.noneOf(ArtifactKind.class);
    if (sources) {
      result.add(SOURCES);
    }
    if (javadoc) {
      result.add(JAVADOC);
    }
    if (artifactPackaging.length == 0 || artifactPackaging.length == 1 && artifactPackaging[0] == null) {
      result.add(ARTIFACT);
    }
    else {
      for (String packaging : artifactPackaging) {
        final ArtifactKind artifact = find(ARTIFACT.getClassifier(), packaging);
        if (artifact != null) {
          result.add(artifact);
        }
      }
    }
    return result;
  }
}
