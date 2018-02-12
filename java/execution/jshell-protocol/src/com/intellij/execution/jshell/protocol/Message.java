package com.intellij.execution.jshell.protocol;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * @author Eugene Zhuravlev
 */
public abstract class Message {
  private String myUid;

  public Message() {
  }

  public Message(String uid) {
    myUid = uid;
  }

  public String getUid() {
    return myUid;
  }

  @XmlAttribute
  public void setUid(String uid) {
    myUid = uid;
  }
}
