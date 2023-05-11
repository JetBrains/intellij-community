// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class IndexVersionRegistrationSink {
  private final Map<ID<?, ?>, IndexVersion.IndexVersionDiff> indexVersionDiffs = new ConcurrentHashMap<>();

  public boolean hasChangedIndexes() {
    return ContainerUtil.find(indexVersionDiffs.values(), diff -> isRebuildRequired(diff)) != null;
  }

  public @NotNull String changedIndices() {
    return buildString(diff -> isRebuildRequired(diff));
  }

  public void logChangedAndFullyBuiltIndices(@NotNull Logger log,
                                             @NotNull String changedIndicesLogMessage,
                                             @NotNull String fullyBuiltIndicesLogMessage) {
    String changedIndices = changedIndices();
    if (!changedIndices.isEmpty()) {
      log.info(changedIndicesLogMessage + changedIndices);
    }
    String fullyBuiltIndices = initiallyBuiltIndices();
    if (!fullyBuiltIndices.isEmpty()) {
      log.info(fullyBuiltIndicesLogMessage + fullyBuiltIndices);

      // IDEA-317780
      detectIdea317780();
    }
  }

  private void detectIdea317780() {
    try {
      Set<String> initiallyBuiltIndexNames = indexVersionDiffs.entrySet().stream()
        .filter(e -> e.getValue() instanceof IndexVersion.IndexVersionDiff.InitialBuild)
        .map(e -> e.getKey().getName())
        .collect(Collectors.toSet());

      List<String> markerIndexes = Arrays.asList("java.fun.expression", "bytecodeAnalysis", "FilenameIndex", "java.null.method.argument",
                                                 "java.binary.plus.expression", "HashFragmentIndex", "IdIndex", "Trigram.Index");

      if (initiallyBuiltIndexNames.size() == markerIndexes.size() && initiallyBuiltIndexNames.containsAll(markerIndexes)) {
        Map.Entry<ID<?, ?>, IndexVersion.IndexVersionDiff> javaNullEntry =
          ContainerUtil.find(indexVersionDiffs.entrySet(), id -> "java.null.method.argument".equals(id.getKey().getName()));

        ID<?, ?> javaNullId = javaNullEntry.getKey();
        IndexVersion.IndexVersionDiff javaNullVersion = javaNullEntry.getValue();

        StringBuilder diagnostics = new StringBuilder();
        Path versionFile = IndexInfrastructure.getVersionFile(javaNullId);
        diagnostics.append("""
                             IDEA-317780 additional diagnostics data. Please enable debug logs for 2 categories:

                             #com.intellij.util.indexing.FileBasedIndexImpl
                             com.intellij.util.indexing.FileBasedIndexImpl

                             and attach the logs to IDEA-317780 ticket when you observe this message next time.
                             """);
        diagnostics.append("Version diff: ").append(javaNullVersion.getLogText()).append("\n");
        diagnostics.append("Version file: ").append(versionFile);
        BasicFileAttributes attr = Files.readAttributes(versionFile, BasicFileAttributes.class);
        diagnostics.append(", exists=").append(Files.exists(versionFile));
        diagnostics.append(", regular=").append(Files.isRegularFile(versionFile));
        diagnostics.append(", creationTime=").append(attr.creationTime());
        diagnostics.append(", lastAccessTime=").append(attr.lastAccessTime());
        diagnostics.append(", lastModifiedTime=").append(attr.lastModifiedTime());
        diagnostics.append(", now=").append(LocalDateTime.now(ZoneId.of("Z"))).append("Z");
        diagnostics.append("\n");

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(versionFile)))) {
          IndexVersion version = new IndexVersion(in);
          diagnostics.append("Version from file: ").append(version).append("\n");
        }
        catch (Exception e) {
          diagnostics.append("cannot read index version: ").append(e).append("\n");
        }

        FileBasedIndexImpl.LOG.error(diagnostics.toString());
      }
    }
    catch (Exception e) {
      FileBasedIndexImpl.LOG.error("IDEA-317780. Detector failed.", e);
    }
  }

  private @NotNull String buildString(@NotNull Predicate<? super IndexVersion.IndexVersionDiff> condition) {
    return indexVersionDiffs
      .entrySet()
      .stream()
      .filter(e -> condition.test(e.getValue()))
      .map(e -> e.getKey().getName() + e.getValue().getLogText())
      .collect(Collectors.joining(","));
  }

  private String initiallyBuiltIndices() {
    return buildString(diff -> diff instanceof IndexVersion.IndexVersionDiff.InitialBuild);
  }

  public <K, V> void setIndexVersionDiff(@NotNull ID<K, V> name, @NotNull IndexVersion.IndexVersionDiff diff) {
    indexVersionDiffs.put(name, diff);
  }

  private static boolean isRebuildRequired(@NotNull IndexVersion.IndexVersionDiff diff) {
    return diff instanceof IndexVersion.IndexVersionDiff.CorruptedRebuild ||
           diff instanceof IndexVersion.IndexVersionDiff.VersionChanged;
  }
}