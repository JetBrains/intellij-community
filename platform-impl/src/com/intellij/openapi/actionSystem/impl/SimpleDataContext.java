package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

public class SimpleDataContext implements DataContext {
  private String myDataId;
  private Object myData;
  private final DataContext myParent;

  private SimpleDataContext(String dataId, Object data, DataContext parent) {
    myDataId = dataId;
    myData = data;
    myParent = parent;
  }

  public Object getData(String dataId) {
    return myDataId.equals(dataId) ? myData : myParent == null ? null : myParent.getData(dataId);
  }

  public static DataContext getSimpleContext(String dataId, Object data, DataContext parent) {
    return new SimpleDataContext(dataId, data, parent);
  }
  public static DataContext getSimpleContext(String dataId, Object data) {
    return getSimpleContext(dataId, data, null);
  }

  public static DataContext getProjectContext(Project project) {
    return getSimpleContext(DataConstants.PROJECT, project);
  }

  
}
