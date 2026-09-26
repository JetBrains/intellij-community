// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.ant.execution;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.input.InputHandler;
import org.apache.tools.ant.input.InputRequest;
import org.apache.tools.ant.input.MultipleChoiceInputRequest;

import java.io.IOException;
import java.util.List;

public class IdeaInputHandler implements InputHandler {
  @Override
  public void handleInput(InputRequest request) throws BuildException {
    final String prompt = request.getPrompt();
    if (prompt == null) {
      throw new BuildException("Prompt is null");
    }
    final SegmentedOutputStream err = IdeaAntLogger2.ourErr;
    if (err == null) {
      throw new BuildException("Selected InputHandler should be used by Intellij IDEA");
    }
    final PacketWriter packet = PacketFactory.ourInstance.createPacket(AntLoggerConstants.INPUT_REQUEST);
    packet.appendLimitedString(prompt);
    packet.appendLimitedString(request.getDefaultValue());
    if (request instanceof MultipleChoiceInputRequest) {
      @SuppressWarnings("unchecked") 
      List<String> choices = ((MultipleChoiceInputRequest)request).getChoices();
      if (choices != null && !choices.isEmpty()) {
        int count = choices.size();
        packet.appendLong(count);
        for (String choice : choices) {
          packet.appendLimitedString(choice);
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
      final byte[] lengthValue = readBytes(4);
      final int length = (toUnsignedInt(lengthValue[0]) << 24) | (toUnsignedInt(lengthValue[1]) << 16) | (toUnsignedInt(lengthValue[2]) << 8) | toUnsignedInt(lengthValue[3]);
      final String input = new String(readBytes(length));
      request.setInput(input);
      if (!request.isInputValid()) {
        throw new BuildException("Invalid input: " + input);
      }
    }
    catch (IOException e) {
      throw new BuildException(e);
    }
  }

  private static int toUnsignedInt(final byte b) {
    return (int)b & 0xFF;
  }

  private static byte[] readBytes(int count) throws IOException {
    byte[] data = new byte[count];
    int read = System.in.read(data);
    if (read != count) {
      throw new IOException("End of input stream");
    }
    return data;
  }
}
