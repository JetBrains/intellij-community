package com.intellij.featureStatistics;

import org.jdom.Element;

public class GroupDescriptor {
  private String myId;
  private String myDisplayName;

  GroupDescriptor() {
  }

  public GroupDescriptor(String id, String displayName) {
    myId = id;
    myDisplayName = displayName;
  }

  public void readExternal(Element element) {
    myId = element.getAttributeValue("id");
    myDisplayName = element.getAttributeValue("name");
  }

  public String getId() {
    return myId;
  }

  public String getDisplayName() {
    return myDisplayName;
  }
}