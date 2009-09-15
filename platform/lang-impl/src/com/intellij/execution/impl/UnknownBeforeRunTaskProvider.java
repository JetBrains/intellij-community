package com.intellij.execution.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import org.jdom.Attribute;
import org.jdom.Element;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 15, 2009
 */
public class UnknownBeforeRunTaskProvider implements BeforeRunTaskProvider<UnknownBeforeRunTaskProvider.UnknownTask>{
  private final Key<UnknownTask> myId;

  public UnknownBeforeRunTaskProvider(String mirrorProviderName) {
    myId = Key.create(mirrorProviderName);
  }

  public Key<UnknownTask> getId() {
    return myId;
  }

  public String getDescription(RunConfiguration runConfiguration, UnknownTask task) {
    return "Unknown task " + myId.toString();
  }

  public void configureTask(RunConfiguration runConfiguration, UnknownTask task) {
  }

  public boolean executeTask(DataContext context, RunConfiguration configuration, UnknownTask task) {
    return true;
  }

  public boolean hasConfigurationButton() {
    return false;
  }

  public UnknownTask createTask(RunConfiguration runConfiguration) {
    return new UnknownTask();
  }

  public static final class UnknownTask extends BeforeRunTask {
    private Element myConfig;

    public UnknownTask() {
    }

    public void readExternal(Element element) {
      myConfig = element;
    }

    public void writeExternal(Element element) {
      if (myConfig != null) {
        element.removeContent();
        final List attributes = myConfig.getAttributes();
        for (Object attribute : attributes) {
         element.setAttribute((Attribute)((Attribute)attribute).clone());
        }
        for (Object child : myConfig.getChildren()) {
          element.addContent((Element)((Element)child).clone());
        }
      }
    }

    public BeforeRunTask clone() {
      return super.clone();
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      UnknownTask that = (UnknownTask)o;

      if (!JDOMUtil.areElementsEqual(myConfig, that.myConfig)) return false;

      return true;
    }

    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (myConfig != null ? myConfig.hashCode() : 0);
      return result;
    }
  }
}
