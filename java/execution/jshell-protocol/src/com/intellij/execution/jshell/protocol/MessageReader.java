// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jshell.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * @author Eugene Zhuravlev
 */
public class MessageReader<T> extends Endpoint {
  private final BufferedReader myIn;
  private final Class<T> myMsgType;

  public MessageReader(InputStream input, Class<T> msgType) {
    myIn = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    myMsgType = msgType;
  }

  public T receive(final Consumer<? super String> unparsedOutputSink) throws IOException {
    while (true) {
      String line = myIn.readLine();
      if (line == null) {
        return null;
      }
      if (MSG_BEGIN.equals(line)) {
        final StringBuilder buf = new StringBuilder();
        for (String body = myIn.readLine(); !MSG_END.equals(body.trim()); body = myIn.readLine()) {
          buf.append(body);
        }
        byte[] bytes = Base64.getDecoder().decode(buf.toString());
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
          return myMsgType.cast(ois.readObject());
        }
        catch (ClassNotFoundException e) {
          throw new IOException(e);
        }
      }
      else {
        unparsedOutputSink.accept(line + "\n");
      }
    }
  }
}