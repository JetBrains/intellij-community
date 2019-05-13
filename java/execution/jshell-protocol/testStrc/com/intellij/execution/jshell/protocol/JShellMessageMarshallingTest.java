// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jshell.protocol;

import org.junit.Test;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * @author Eugene Zhuravlev
 */
public class JShellMessageMarshallingTest {
  private static final int PIPE_SIZE = 32 << 10;
  private static final Event[] EMPTY_EVENT_ARRAY = new Event[0];

  @Test(timeout = 10000)
  public void testSendReceive() throws Exception {
    final PipedInputStream clientIn = new PipedInputStream(PIPE_SIZE);
    final PipedOutputStream serverOut = new PipedOutputStream(clientIn);
    final PipedInputStream serverIn = new PipedInputStream(PIPE_SIZE);
    final PipedOutputStream clientOut = new PipedOutputStream(serverIn);
    final MessageWriter<Request> clientWriter = new MessageWriter<>(clientOut);
    final MessageReader<Request> serverReader = new MessageReader<>(serverIn, Request.class);
    final MessageWriter<Response> serverWriter = new MessageWriter<>(serverOut);
    final MessageReader<Response> clientReader = new MessageReader<>(clientIn, Response.class);

    final Request request = new Request(UUID.randomUUID().toString(), Request.Command.EVAL,
                                        "System.out.println(\"Hello, World!\");\n int var = 7 + 7;");
    request.addClasspathItem("C:/work/path1");
    request.addClasspathItem("C:/work/path2");
    final List<String> requestClasspath = request.getClassPath();

    clientWriter.send(request);
    final Request receivedRequest = serverReader.receive(s -> {});
    assertEquals(request.getUid(), receivedRequest.getUid());
    assertEquals(request.getCodeText(), receivedRequest.getCodeText());
    final List<String> receivedClasspath = receivedRequest.getClassPath();
    assertEquals(requestClasspath, receivedClasspath);

    final CodeSnippet snippet = new CodeSnippet("code-snippet-id", CodeSnippet.Kind.EXPRESSION, CodeSnippet.SubKind.OTHER_EXPRESSION_SUBKIND,
                                                "a+b", "expression:a+b");
    final Event event1 = new Event(null, null, CodeSnippet.Status.UNKNOWN, CodeSnippet.Status.NONEXISTENT, null);
    event1.setExceptionText("some exception");
    event1.setDiagnostic("error diagnostic");
    final Event event2 = new Event(snippet, null, CodeSnippet.Status.VALID, CodeSnippet.Status.NONEXISTENT, "14");
    final Response response = new Response(request.getUid(), event1, event2);
    serverWriter.send(response);

    final Response receivedResponse = clientReader.receive(s -> {});
    assertEquals(response.getUid(), receivedResponse.getUid());
    final Event[] events = response.getEvents().toArray(EMPTY_EVENT_ARRAY);
    final Event[] receivedEvents = receivedResponse.getEvents().toArray(EMPTY_EVENT_ARRAY);

    assertEquals(events.length, receivedEvents.length);
    for (int i = 0; i < events.length; i++) {
      final Event expectedEvent = events[i];
      final Event receivedEvent = receivedEvents[i];
      assertEquals(expectedEvent.getSnippet(), receivedEvent.getSnippet());
      assertEquals(expectedEvent.getCauseSnippet(), receivedEvent.getCauseSnippet());
      assertEquals(expectedEvent.getStatus(), receivedEvent.getStatus());
      assertEquals(expectedEvent.getPreviousStatus(), receivedEvent.getPreviousStatus());
      assertEquals(expectedEvent.getValue(), receivedEvent.getValue());
      assertEquals(expectedEvent.getExceptionText(), receivedEvent.getExceptionText());
    }
  }
}