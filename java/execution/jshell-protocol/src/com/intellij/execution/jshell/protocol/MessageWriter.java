package com.intellij.execution.jshell.protocol;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * @author Eugene Zhuravlev
 */
public class MessageWriter<T extends Message> extends Endpoint {
  private final BufferedWriter myOut;
  private final JAXBContext myContext;

  public MessageWriter(OutputStream output, Class<T> msgType) throws Exception {
    myOut = new BufferedWriter(new OutputStreamWriter(output));
    myContext = JAXBContext.newInstance(msgType);
  }

  public void send(T message) throws IOException {
    try {
      myOut.newLine();
      myOut.write(MSG_BEGIN);
      myOut.newLine();
      myContext.createMarshaller().marshal(message, myOut);
    }
    catch (JAXBException e) {
      throw new IOException(e);
    }
    finally {
      myOut.newLine();
      myOut.write(MSG_END);
      myOut.newLine();
      myOut.flush();
    }
  }

}
