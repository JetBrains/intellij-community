// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.libraries.testcontainers;

import com.github.dockerjava.api.model.AuthConfig;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;
import org.testcontainers.utility.RegistryAuthLocator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pulls Testcontainers images through registry mirrors given in {@value #MIRRORS_ENV}, a comma-separated list of
 * {@code from=to} prefixes, for example {@code docker.io=mirror.example.com/docker-hub,registry.example.com/p=mirror.example.com/p}.
 * Images without a registry match {@code docker.io}. The longest matching prefix wins.
 * <p>
 * A rule applies only when the Docker config holds credentials for its target registry, so a build without the
 * {@code docker login} keeps the original image instead of failing on an authenticated mirror. Without the variable,
 * nothing changes.
 */
public final class RegistryMirrorImageNameSubstitutor extends ImageNameSubstitutor {
  static final String MIRRORS_ENV = "INTELLIJ_TESTCONTAINERS_REGISTRY_MIRRORS";
  private static final String DOCKER_HUB = "docker.io";

  private final List<String[]> rules = parse(System.getenv(MIRRORS_ENV));

  @Override
  public DockerImageName apply(DockerImageName original) {
    String unversioned = original.getUnversionedPart();
    String name = original.getRegistry().isEmpty() ? DOCKER_HUB + "/" + unversioned : unversioned;
    for (String[] rule : rules) {
      if (!name.equals(rule[0]) && !name.startsWith(rule[0] + "/")) continue;
      String version = original.asCanonicalNameString().substring(unversioned.length());
      DockerImageName mirrored = DockerImageName.parse(rule[1] + name.substring(rule[0].length()) + version)
        .asCompatibleSubstituteFor(original);
      return hasCredentials(mirrored) ? mirrored : original;
    }
    return original;
  }

  @Override
  protected String getDescription() {
    return "registry mirrors from " + MIRRORS_ENV;
  }

  private static boolean hasCredentials(DockerImageName image) {
    AuthConfig auth = RegistryAuthLocator.instance().lookupAuthConfig(image, new AuthConfig());
    return auth.getUsername() != null || auth.getAuth() != null || auth.getIdentitytoken() != null;
  }

  private static List<String[]> parse(String value) {
    List<String[]> rules = new ArrayList<>();
    if (value == null) return rules;
    for (String entry : value.split(",")) {
      int eq = entry.indexOf('=');
      if (eq <= 0) continue;
      String from = trimSlashes(entry.substring(0, eq));
      String to = trimSlashes(entry.substring(eq + 1));
      if (!from.isEmpty() && !to.isEmpty()) rules.add(new String[]{from, to});
    }
    rules.sort(Comparator.comparingInt((String[] rule) -> rule[0].length()).reversed());
    return rules;
  }

  private static String trimSlashes(String s) {
    String trimmed = s.trim();
    while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
    return trimmed;
  }
}
