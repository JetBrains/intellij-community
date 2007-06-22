package com.intellij.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class LocalHistoryConfiguration implements JDOMExternalizable, ApplicationComponent {
  private static final long DEFAULT_PURGING_PERIOD = 1000 * 60 * 60 * 24 * 3; // 3 days

  public long PURGE_PERIOD = DEFAULT_PURGING_PERIOD;

  public boolean ADD_LABEL_ON_PROJECT_OPEN = true;
  public boolean ADD_LABEL_ON_PROJECT_COMPILATION = true;
  public boolean ADD_LABEL_ON_FILE_PACKAGE_COMPILATION = true;
  public boolean ADD_LABEL_ON_PROJECT_MAKE = true;
  public boolean ADD_LABEL_ON_RUNNING = true;
  public boolean ADD_LABEL_ON_DEBUGGING = true;
  public boolean ADD_LABEL_ON_UNIT_TEST_PASSED = true;
  public boolean ADD_LABEL_ON_UNIT_TEST_FAILED = true;

  public boolean SHOW_CHANGES_ONLY = false;

  public static LocalHistoryConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(LocalHistoryConfiguration.class);
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public String getComponentName() {
    return "LocalHistoryConfiguration";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
