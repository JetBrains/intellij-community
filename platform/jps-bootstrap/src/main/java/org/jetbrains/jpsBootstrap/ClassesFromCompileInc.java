// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import com.google.common.base.StandardSystemProperty;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.Pair;
import groovy.transform.CompileStatic;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.verbose;

public class ClassesFromCompileInc {
  public final static String MANIFEST_JSON_URL_ENV_NAME = "JPS_BOOTSTRAP_MANIFEST_JSON_URL";

  public static void downloadProjectClasses(JpsProject project, Path communityRoot, Collection<JpsModule> modules) throws IOException, InterruptedException {
    String manifestUrl = System.getenv(MANIFEST_JSON_URL_ENV_NAME);
    if (manifestUrl == null || manifestUrl.isBlank()) {
      throw new IllegalStateException("Env variable '" + MANIFEST_JSON_URL_ENV_NAME + "' is missing or empty");
    }
    verbose("Got manifest json url '" + manifestUrl + "' from $" + MANIFEST_JSON_URL_ENV_NAME);

    final Path manifest = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, URI.create(manifestUrl));
    Map<JpsModule, Path> productionModuleOutputs = downloadProductionPartsFromMetadataJson(manifest, communityRoot, modules);
    assignModuleOutputs(project, productionModuleOutputs);
  }

  private static void assignModuleOutputs(JpsProject project, Map<JpsModule, Path> productionModuleOutputs) {
    Path nonExistentPath = Path.of(
      System.getProperty(StandardSystemProperty.JAVA_IO_TMPDIR.key()),
      UUID.randomUUID().toString());

    // Set it to non-existent path since we won't run build and standard built output won't be available anyway
    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project);
    projectExtension.setOutputUrl(JpsPathUtil.pathToUrl(nonExistentPath.toString()));

    for (Map.Entry<JpsModule, Path> entry : productionModuleOutputs.entrySet()) {
      JpsModule module = entry.getKey();
      final JpsJavaModuleExtension javaExtension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module);
      javaExtension.setOutputUrl(JpsPathUtil.pathToUrl(entry.getValue().toString()));
      javaExtension.setInheritOutput(false);
    }
  }

  private static Map<JpsModule, Path> downloadProductionPartsFromMetadataJson(Path metadataJson, Path communityRoot, Collection<JpsModule> modules) throws InterruptedException, IOException {
    CompilationPartsMetadata partsMetadata;

    try (BufferedReader manifestReader = Files.newBufferedReader(metadataJson, StandardCharsets.UTF_8)) {
      partsMetadata = new Gson().fromJson(manifestReader, CompilationPartsMetadata.class);
    }

    if (partsMetadata.files.isEmpty()) {
      throw new IllegalStateException("partsMetadata.files is empty, check " + metadataJson);
    }

    List<Callable<Pair<JpsModule, Path>>> tasks = new ArrayList<>();
    for (final JpsModule module : modules) {
      Callable<Pair<JpsModule, Path>> c = () -> {
        String modulePrefix = "production/" + module.getName();

        String hash = partsMetadata.files.get(modulePrefix);
        if (hash == null) {
          throw new IllegalStateException("Unable to find module output by name '" + modulePrefix + "' in " + metadataJson);
        }

        URI outputPartUri = URI.create(partsMetadata.serverUrl + "/" + partsMetadata.prefix + "/" + modulePrefix + "/" + hash + ".jar");
        final Path outputPart = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, outputPartUri);
        final Path outputPartExtracted = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, outputPart);

        return Pair.pair(module, outputPartExtracted);
      };
      tasks.add(c);
    }

    return JpsBootstrapUtil.executeTasksInParallel(tasks)
      .stream().collect(Collectors.toUnmodifiableMap(pair -> pair.getFirst(), pair -> pair.getSecond()));
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
