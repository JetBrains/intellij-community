package com.intellij.rt.ant.execution;

import com.intellij.rt.execution.junit.segments.PacketWriter;
import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
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
    String prompt = request.getPrompt();
    if (prompt == null) throw new BuildException("Prompt is null");
    SegmentedOutputStream out = IdeaAntLogger2.ourOut;
    SegmentedOutputStream err = IdeaAntLogger2.ourErr;
    if (out == null || err == null)
      throw new BuildException("Selected InputHandler should be used by Intellij IDEA");
    PacketWriter packet = PacketFactory.ourInstance.createPacket(IdeaAntLogger2.INPUT_REQUEST);
    packet.appendLimitedString(prompt);
    if (request instanceof MultipleChoiceInputRequest) {
      Vector choices = ((MultipleChoiceInputRequest)request).getChoices();
      if (choices != null && choices.size() > 0) {
        int count = choices.size();
        packet.appendLong(count);
        for (int i = 0; i < count; i++)
          packet.appendLimitedString((String)choices.elementAt(i));
      } else packet.appendLong(0);
    } else packet.appendLong(0);
    packet.sendThrough(out);
    packet.sendThrough(err);
    try {
      byte[] replayLength = readBytes(4);
      int length = ((int)replayLength[0] << 24) | ((int)replayLength[1] << 16) | ((int)replayLength[2] << 8) | replayLength[3];
      byte[] replay = readBytes(length);
      String input = new String(replay);
      request.setInput(input);
      if (!request.isInputValid()) throw new BuildException("Invalid input: " + input);
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
