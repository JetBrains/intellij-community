/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.builders;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.DoneSomethingNotification;
import org.jetbrains.jps.incremental.storage.OutputToTargetRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author nik
 */
public class BuildResult implements MessageHandler {
  private final List<BuildMessage> myErrorMessages;
  private final List<BuildMessage> myWarnMessages;
  private final List<BuildMessage> myInfoMessages;
  private boolean myUpToDate = true;
  private String myMappingsDump;

  public BuildResult() {
    myErrorMessages = new ArrayList<BuildMessage>();
    myWarnMessages = new ArrayList<BuildMessage>();
    myInfoMessages = new ArrayList<BuildMessage>();
  }

  void storeMappingsDump(ProjectDescriptor pd) throws IOException {
    final ByteArrayOutputStream dump = new ByteArrayOutputStream();

    final PrintStream stream = new PrintStream(dump);
    try {
      pd.dataManager.getMappings().toStream(stream);
      dumpSourceToOutputMappings(pd, stream);
    }
    finally {
      stream.close();
    }

    dump.close();
    myMappingsDump = dump.toString();
  }

  private static void dumpSourceToOutputMappings(ProjectDescriptor pd, PrintStream stream) throws IOException {
    List<BuildTarget<?>> targets = new ArrayList<BuildTarget<?>>(pd.getBuildTargetIndex().getAllTargets());
    Collections.sort(targets, new Comparator<BuildTarget<?>>() {
      @Override
      public int compare(BuildTarget<?> o1, BuildTarget<?> o2) {
        return StringUtil.comparePairs(o1.getTargetType().getTypeId(), o1.getId(), o2.getTargetType().getTypeId(), o2.getId(), false);
      }
    });
    final TIntObjectHashMap<BuildTarget<?>> id2Target = new TIntObjectHashMap<BuildTarget<?>>();
    for (BuildTarget<?> target : targets) {
      id2Target.put(pd.dataManager.getTargetsState().getBuildTargetId(target), target);
    }
    TIntObjectHashMap<String> hashCodeToOutputPath = new TIntObjectHashMap<String>();
    for (BuildTarget<?> target : targets) {
      stream.println("Begin Of SourceToOutput (target " + getTargetIdWithTypeId(target) + ")");
      SourceToOutputMapping map = pd.dataManager.getSourceToOutputMap(target);
      List<String> sourcesList = new ArrayList<String>(map.getSources());
      Collections.sort(sourcesList);
      for (String source : sourcesList) {
        List<String> outputs = new ArrayList<String>(ObjectUtils.notNull(map.getOutputs(source), Collections.<String>emptySet()));
        Collections.sort(outputs);
        for (String output : outputs) {
          hashCodeToOutputPath.put(FileUtil.pathHashCode(output), output);
        }
        String sourceToCompare = SystemInfo.isFileSystemCaseSensitive ? source : source.toLowerCase(Locale.US);
        stream.println(" " + sourceToCompare + " -> " + StringUtil.join(outputs, ","));
      }
      stream.println("End Of SourceToOutput (target " + getTargetIdWithTypeId(target) + ")");
    }


    OutputToTargetRegistry registry = pd.dataManager.getOutputToTargetRegistry();
    List<Integer> keys = new ArrayList<Integer>(registry.getKeys());
    Collections.sort(keys);
    stream.println("Begin Of OutputToTarget");
    for (Integer key : keys) {
      TIntHashSet targetsIds = registry.getState(key);
      if (targetsIds == null) continue;
      final List<String> targetsNames = new ArrayList<String>();
      targetsIds.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          BuildTarget<?> target = id2Target.get(value);
          targetsNames.add(target != null ? getTargetIdWithTypeId(target) : "<unknown " + value + ">");
          return true;
        }
      });
      Collections.sort(targetsNames);
      stream.println(hashCodeToOutputPath.get(key) + " -> " + targetsNames);
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
      Function<BuildMessage, String> toStringFunction = StringUtil.createToStringFunction(BuildMessage.class);
      fail("Build failed.\n" +
           "Errors:\n" + StringUtil.join(myErrorMessages, toStringFunction, "\n") + "\n" +
           "Info messages:\n" + StringUtil.join(myInfoMessages, toStringFunction, "\n"));
    }
  }

  @NotNull
  public List<BuildMessage> getMessages(@NotNull BuildMessage.Kind kind) {
    if (kind == BuildMessage.Kind.ERROR) return myErrorMessages;
    else if (kind == BuildMessage.Kind.WARNING) return myWarnMessages;
    else return myInfoMessages;
  }
}
