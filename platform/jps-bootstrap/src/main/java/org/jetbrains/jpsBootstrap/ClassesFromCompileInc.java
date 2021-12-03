// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.Pair;
import groovy.transform.CompileStatic;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.verbose;

public class ClassesFromCompileInc {
  public final static String MANIFEST_JSON_URL_ENV_NAME = "JPS_BOOTSTRAP_MANIFEST_JSON_URL";

  public static void downloadProjectClasses(JpsProject project, Path communityRoot) throws IOException, InterruptedException {
    String manifestUrl = System.getenv(MANIFEST_JSON_URL_ENV_NAME);
    if (manifestUrl == null || manifestUrl.isBlank()) {
      throw new IllegalStateException("Env variable '" + MANIFEST_JSON_URL_ENV_NAME + "' is missing or empty");
    }
    verbose("Got manifest json url '" + manifestUrl + "' from $" + MANIFEST_JSON_URL_ENV_NAME);

    final Path manifest = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, URI.create(manifestUrl));
    Map<String, Path> parts = downloadPartsFromMetadataJson(manifest, communityRoot);
    assignModuleOutputs(project, parts);
  }

  private static void assignModuleOutputs(JpsProject project, Map<String, Path> parts) {
    Map<String, Path> partsCopy = new HashMap<>(parts);

    for (JpsModule module : project.getModules()) {
      boolean hasProduction = false;
      boolean hasTests = false;

      for (JpsModuleSourceRoot sourceRoot : module.getSourceRoots()) {
        if (sourceRoot.getRootType() == JavaSourceRootType.SOURCE || sourceRoot.getRootType() == JavaResourceRootType.RESOURCE) {
          hasProduction = true;
        } else if (sourceRoot.getRootType() == JavaSourceRootType.TEST_SOURCE || sourceRoot.getRootType() == JavaResourceRootType.TEST_RESOURCE) {
          hasTests = true;
        } else {
          throw new IllegalStateException("Unsupported source root type " + sourceRoot + " for module " + module.getName());
        }
      }

      final JpsJavaModuleExtension javaExtension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module);
      if (hasTests) {
        final String key = "test/" + module.getName();
        Path testOutputPath = partsCopy.remove(key);
        if (testOutputPath == null) {
          throw new IllegalStateException("Output for " + key + " was not found");
        }

        javaExtension.setTestOutputUrl(JpsPathUtil.pathToUrl(testOutputPath.toString()));
      }

      if (hasProduction) {
        final String key = "production/" + module.getName();
        Path productionOutputPath = partsCopy.remove(key);
        if (productionOutputPath == null) {
          throw new IllegalStateException("Output for " + key + " was not found");
        }

        javaExtension.setOutputUrl(JpsPathUtil.pathToUrl(productionOutputPath.toString()));
      }
    }

    if (!partsCopy.isEmpty()) {
      throw new IllegalStateException("After processing all project modules some entries left: " +
        String.join(" ", partsCopy.keySet()));
    }
  }

  private static Map<String, Path> downloadPartsFromMetadataJson(Path metadataJson, Path communityRoot) throws InterruptedException, IOException {
    CompilationPartsMetadata partsMetadata;
    try (BufferedReader manifestReader = Files.newBufferedReader(metadataJson, StandardCharsets.UTF_8)) {
      partsMetadata = new Gson().fromJson(manifestReader, CompilationPartsMetadata.class);
    }

    if (partsMetadata.files.isEmpty()) {
      throw new IllegalStateException("partsMetadata.files is empty, check " + metadataJson);
    }

    List<Callable<Pair<String, Path>>> tasks = new ArrayList<>();
    for (final Map.Entry<String, String> entry : partsMetadata.files.entrySet()) {
      Callable<Pair<String, Path>> c = () -> {
        String modulePrefix = entry.getKey();
        String hash = entry.getValue();

        URI outputPartUri = URI.create(partsMetadata.serverUrl + "/" + partsMetadata.prefix + "/" + modulePrefix + "/" + hash + ".jar");
        final Path outputPart = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, outputPartUri);
        final Path outputPartExtracted = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, outputPart);

        System.out.println(modulePrefix + " = " + outputPartExtracted);

        return Pair.pair(modulePrefix, outputPartExtracted);
      };
      tasks.add(c);
    }

    return JpsBootstrapUtil.executeTasksInParallel(tasks)
      .stream().collect(Collectors.toUnmodifiableMap(pair -> pair.getFirst(), pair -> pair.getSecond()));
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    downloadPartsFromMetadataJson(Paths.get("D:\\temp\\metadata.json"), Paths.get("D:\\Work\\intellij\\community"));
  }

  @CompileStatic
  private static final class CompilationPartsMetadata {
    @SerializedName("server-url")
    public String serverUrl;
    public String prefix;

    /**
     * Map compilation part path to a hash, for now SHA-256 is used.
     * sha256(file) == hash, though that may be changed in the future.
     */
    public Map<String, String> files;
  }
}
