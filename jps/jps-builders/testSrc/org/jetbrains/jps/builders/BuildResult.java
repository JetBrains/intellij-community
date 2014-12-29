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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.DoneSomethingNotification;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

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
    }
    finally {
      stream.close();
    }

    dump.close();
    myMappingsDump = dump.toString();
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
