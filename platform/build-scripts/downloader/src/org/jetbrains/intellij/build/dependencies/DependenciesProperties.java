// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApiStatus.Internal
public final class DependenciesProperties {
  private final Map<String, String> dependencies = new TreeMap<>();

  public DependenciesProperties(BuildDependenciesCommunityRoot communityRoot, Path... customPropertyFiles) throws IOException {
    var communityPropertiesFile = communityRoot.getCommunityRoot()
      .resolve("build")
      .resolve("dependencies")
      .resolve("dependencies.properties");
    var ultimatePropertiesFile = communityRoot.getCommunityRoot()
      .getParent()
      .resolve("build")
      .resolve("dependencies.properties");
    //noinspection SimplifyStreamApiCallChains
    var propertyFiles = Stream.concat(
      Stream.of(customPropertyFiles),
      Stream.of(communityPropertiesFile, ultimatePropertiesFile).filter(Files::exists)
    ).distinct().collect(Collectors.toList());
    for (Path propertyFile : propertyFiles) {
      try (var file = Files.newInputStream(propertyFile)) {
        var properties = new Properties();
        properties.load(file);
        properties.forEach((key, value) -> {
          if (dependencies.containsKey(key)) {
            throw new IllegalStateException("Key '" + key + "' from " + propertyFile + " is already defined");
          }
          dependencies.put(key.toString(), value.toString());
        });
      }
    }
    if (dependencies.isEmpty()) {
      throw new IllegalStateException("No dependencies are defined");
    }
  }

  @Override
  public String toString() {
    //noinspection SimplifyStreamApiCallChains,SSBasedInspection
    return String.join("\n", dependencies.entrySet().stream()
      .map(it -> it.getKey() + "=" + it.getValue())
      .collect(Collectors.toList()));
  }

  @NotNull
  public String property(String name) {
    var property = dependencies.get(name);
    if (property == null) {
      throw new IllegalArgumentException("'" + name + "' is unknown key: " + this);
    }
    return property;
  }

  public void copy(Path copy) throws IOException {
    try (var file = Files.newBufferedWriter(copy, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      file.write(toString());
    }
    if (!dependencies.containsKey("jdkBuild")) {
      throw new IllegalStateException("'jdkBuild' key is required for backward compatibility with gradle-intellij-plugin");
    }
  }
}
