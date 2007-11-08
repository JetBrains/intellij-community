package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

public class SimpleDataContext implements DataContext {
  private String myDataId;
  private Object myData;

  private SimpleDataContext( String dataId, Object data ) {
        myDataId = dataId;
	myData = data;
  }

  public Object getData(String dataId) {
    return myDataId.equals ( dataId ) ? myData : null;
  }

  public static DataContext getSimpleContext(String dataId, Object data) {
    return new SimpleDataContext(dataId, data);
  }

  public static DataContext getProjectContext(Project project) {
    return getSimpleContext(DataConstants.PROJECT, project);
  }
}
