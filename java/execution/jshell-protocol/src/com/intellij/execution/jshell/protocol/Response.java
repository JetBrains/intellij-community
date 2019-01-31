// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jshell.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class Response extends Message {
  private List<Event> myEvents;

  @SuppressWarnings("unused")
  public Response() { }

  public Response(String uid, Event... events) {
    super(uid);
    Collections.addAll(myEvents = new ArrayList<>(), events);
  }

  public List<Event> getEvents() {
    return myEvents;
  }

  public void addEvent(Event event) {
    List<Event> events = myEvents;
    if (events == null) {
      events = new ArrayList<>();
      myEvents = events;
    }
    events.add(event);
  }
}