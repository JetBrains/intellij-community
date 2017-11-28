package com.intellij.execution.jshell.protocol;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * @author Eugene Zhuravlev
 * Date: 12-Jun-17
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
