package com.intellij.execution.jshell.protocol;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
@XmlRootElement
public class Response extends Message{
  private List<Event> myEvents;

  public Response() {
  }

  public Response(String uid, Event... events) {
    super(uid);
    Collections.addAll(myEvents = new ArrayList<>(), events);
  }

  public List<Event> getEvents() {
    return myEvents;
  }

  @XmlElement
  public void setEvents(List<Event> events) {
    myEvents = events;
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
