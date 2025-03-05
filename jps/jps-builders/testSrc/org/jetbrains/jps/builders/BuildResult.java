// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.DoneSomethingNotification;
import org.jetbrains.jps.incremental.storage.OutputToTargetRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.junit.Assert.*;

public final class BuildResult implements MessageHandler {
  private final List<BuildMessage> myErrorMessages;
  private final List<BuildMessage> myWarnMessages;
  private final List<BuildMessage> myInfoMessages;
  private boolean myUpToDate = true;
  private String myMappingsDump;

  public BuildResult() {
    myErrorMessages = new ArrayList<>();
    myWarnMessages = new ArrayList<>();
    myInfoMessages = new ArrayList<>();
  }

  void storeMappingsDump(ProjectDescriptor pd) throws IOException {
    final ByteArrayOutputStream dump = new ByteArrayOutputStream();

    try (PrintStream stream = new PrintStream(dump, false, StandardCharsets.UTF_8)) {
      Mappings mappings = pd.dataManager.getMappings();
      if (mappings != null) {
        mappings.toStream(stream);
      }
      dumpSourceToOutputMappings(pd, stream);
    }

    dump.close();
    myMappingsDump = dump.toString();
  }

  @SuppressWarnings("SSBasedInspection")
  private static void dumpSourceToOutputMappings(@NotNull ProjectDescriptor projectDescriptor, @NotNull PrintStream stream) throws IOException {
    List<BuildTarget<?>> targets = new ArrayList<>(projectDescriptor.getBuildTargetIndex().getAllTargets());
    targets.sort((o1, o2) -> {
      return StringUtil.comparePairs(o1.getTargetType().getTypeId(), o1.getId(), o2.getTargetType().getTypeId(), o2.getId(), false);
    });

    Int2ObjectMap<BuildTarget<?>> idToTarget = new Int2ObjectOpenHashMap<>();
    for (BuildTarget<?> target : targets) {
      idToTarget.put(projectDescriptor.dataManager.getTargetStateManager().getBuildTargetId(target), target);
    }

    Int2ObjectMap<Path> hashCodeToOutputPath = new Int2ObjectOpenHashMap<>();
    for (BuildTarget<?> target : targets) {
      stream.println("Begin Of SourceToOutput (target " + getTargetIdWithTypeId(target) + ")");
      SourceToOutputMapping map = projectDescriptor.dataManager.getSourceToOutputMap(target);
      List<Path> sourceList = new ObjectArrayList<>(map.getSourceFileIterator());
      sourceList.sort(null);
      for (Path source : sourceList) {
        List<Path> outputs = new ArrayList<>(Objects.requireNonNullElse(map.getOutputs(source), List.of()));
        outputs.sort(null);
        for (Path output : outputs) {
          hashCodeToOutputPath.put(FileUtilRt.pathHashCode(output.toString()), output);
        }
        String sourceToCompare = SystemInfoRt.isFileSystemCaseSensitive ? source.toString() : source.toString().toLowerCase(Locale.US);
        stream.println(" " + FileUtilRt.toSystemIndependentName(sourceToCompare) +
                       " -> " +
                       outputs.stream().map(it -> FileUtilRt.toSystemIndependentName(it.toString())).toList());
      }
      stream.println("End Of SourceToOutput (target " + getTargetIdWithTypeId(target) + ")");
    }

    OutputToTargetRegistry registry = (OutputToTargetRegistry)projectDescriptor.dataManager.getOutputToTargetMapping();
    List<Integer> keys = registry.getAllKeys();
    if (keys.size() > 1) {
      keys.sort(null);
    }
    stream.println("Begin Of OutputToTarget");
    for (Integer key : keys) {
      IntSet targetsIds = registry.getState(key);
      if (targetsIds == null) {
        continue;
      }

      List<String> targetsNames = new ArrayList<>();
      targetsIds.forEach(value -> {
        BuildTarget<?> target = idToTarget.get(value);
        targetsNames.add(target != null ? getTargetIdWithTypeId(target) : "<unknown " + value + ">");
      });
      targetsNames.sort(null);
      stream.println(hashCodeToOutputPath.get(key.intValue()) + " -> " + targetsNames);
    }
    stream.println("End Of OutputToTarget");
  }

  @NotNull
  private static String getTargetIdWithTypeId(BuildTarget<?> target) {
    return target.getTargetType().getTypeId() + ":" + target.getId();
  }

  @Override
  public void processMessage(BuildMessage msg) {
    if (msg.getKind() == BuildMessage.Kind.ERROR) {
      myErrorMessages.add(msg);
      myUpToDate = false;
    }
    else if (msg.getKind() == BuildMessage.Kind.WARNING) {
      myWarnMessages.add(msg);
    }
    else {
      myInfoMessages.add(msg);
    }
    if (msg instanceof DoneSomethingNotification) {
      myUpToDate = false;
    }
  }

  public String getMappingsDump() {
    return myMappingsDump;
  }

  public void assertUpToDate() {
    assertTrue("Project sources weren't up to date", myUpToDate);
  }

  public void assertFailed() {
    assertFalse("Build not failed as expected", isSuccessful());
  }

  public boolean isSuccessful() {
    return myErrorMessages.isEmpty();
  }

  public void assertSuccessful() {
    if (!isSuccessful()) {
      fail("Build failed.\n" +
           "Errors:\n" + StringUtil.join(myErrorMessages, "\n") + "\n" +
           "Info messages:\n" + StringUtil.join(myInfoMessages, "\n"));
    }
  }

  @NotNull
  public List<BuildMessage> getMessages(@NotNull BuildMessage.Kind kind) {
    if (kind == BuildMessage.Kind.ERROR) return myErrorMessages;
    else if (kind == BuildMessage.Kind.WARNING) return myWarnMessages;
    else return myInfoMessages;
  }
}
