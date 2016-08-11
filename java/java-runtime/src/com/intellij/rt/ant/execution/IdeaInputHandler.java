/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.rt.ant.execution;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.input.InputHandler;
import org.apache.tools.ant.input.InputRequest;
import org.apache.tools.ant.input.MultipleChoiceInputRequest;

import java.io.IOException;
import java.util.Vector;

/**
 * @author dyoma
 */
public class IdeaInputHandler implements InputHandler {
  public void handleInput(InputRequest request) throws BuildException {
    final String prompt = request.getPrompt();
    if (prompt == null) {
      throw new BuildException("Prompt is null");
    }
    final SegmentedOutputStream err = IdeaAntLogger2.ourErr;
    if (err == null) {
      throw new BuildException("Selected InputHandler should be used by Intellij IDEA");
    }
    final PacketWriter packet = PacketFactory.ourInstance.createPacket(IdeaAntLogger2.INPUT_REQUEST);
    packet.appendLimitedString(prompt);
    packet.appendLimitedString(request.getDefaultValue());
    if (request instanceof MultipleChoiceInputRequest) {
      Vector choices = ((MultipleChoiceInputRequest)request).getChoices();
      if (choices != null && choices.size() > 0) {
        int count = choices.size();
        packet.appendLong(count);
        for (int i = 0; i < count; i++) {
          packet.appendLimitedString((String)choices.elementAt(i));
        }
      }
      else {
        packet.appendLong(0);
      }
    }
    else {
      packet.appendLong(0);
    }
    packet.sendThrough(err);
    try {
      final byte[] replayLength = readBytes(4);
      final int length = ((int)replayLength[0] << 24) | ((int)replayLength[1] << 16) | ((int)replayLength[2] << 8) | replayLength[3];
      final byte[] replay = readBytes(length);
      final String input = new String(replay);
      request.setInput(input);
      if (!request.isInputValid()) {
        throw new BuildException("Invalid input: " + input);
      }
    }
    catch (IOException e) {
      throw new BuildException(e);
    }
  }

  private byte[] readBytes(int count) throws IOException {
    byte[] replayLength = new byte[count];
    int read = System.in.read(replayLength);
    if (read != count) throw new IOException("End of input stream");
    return replayLength;
  }
}
