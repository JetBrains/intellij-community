/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.aether;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public enum ArtifactKind {
  ARTIFACT("", "jar"), SOURCES("sources", "jar"), JAVADOC("javadoc", "jar"),
  ANNOTATIONS("annotations", "zip"), AAR_ARTIFACT("", "aar"), POM("", "pom"),
  ALL("all", "jar"), HTTP("http", "jar"), DLL("", "dll"),
  ZIP("", "zip");

  private final String myClassifier;
  private final String myExtension;

  ArtifactKind(String classifier, String extension) {
    myClassifier = classifier;
    myExtension = extension;
  }

  @NotNull
  public String getClassifier() {
    return myClassifier;
  }

  @NotNull
  public String getExtension() {
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
