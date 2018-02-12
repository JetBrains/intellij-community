package com.intellij.execution.jshell.protocol;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.*;
import java.util.function.Consumer;

/**
 * @author Eugene Zhuravlev
 */
public class MessageReader<T> extends Endpoint {
  private final BufferedReader myIn;
  private final JAXBContext myContext;

  public MessageReader(InputStream input, Class<T> msgType) throws Exception {
    myIn = new BufferedReader(new InputStreamReader(input));
    myContext = JAXBContext.newInstance(msgType);
  }

  public T receive(final Consumer<String> unparsedOutputSink) throws IOException {
    while (true) {
      String line = myIn.readLine();
      if (line == null) {
        return null;
      }
      if (MSG_BEGIN.equals(line)) {
        final StringBuilder buf = new StringBuilder();
        for (String body = myIn.readLine(); !MSG_END.equals(body.trim()); body = myIn.readLine()) {
          buf.append(body).append("\n");
        }
        try {
          //noinspection unchecked
          return (T)myContext.createUnmarshaller().unmarshal(new StringReader(buf.toString()));
        }
        catch (JAXBException e) {
          throw new IOException(e);
        }
      }
      else {
        unparsedOutputSink.accept(line + "\n");
      }
    }
  }

}
