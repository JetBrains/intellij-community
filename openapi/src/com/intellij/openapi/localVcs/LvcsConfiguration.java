/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @author mike
 */
public class LvcsConfiguration implements JDOMExternalizable, ApplicationComponent {

  public boolean LOCAL_VCS_ENABLED = true;
  public static final int INITIAL_PURGING_PERIOD = 1000 * 60 * 60 * 24 * 3;
  public long LOCAL_VCS_PURGING_PERIOD = INITIAL_PURGING_PERIOD;
  public boolean ADD_LABEL_ON_PROJECT_OPEN = true;
  public boolean ADD_LABEL_ON_PROJECT_COMPILATION = true;
  public boolean ADD_LABEL_ON_FILE_PACKAGE_COMPILATION = true;
  public boolean ADD_LABEL_ON_PROJECT_MAKE = true;
  public boolean ADD_LABEL_ON_RUNNING = true;
  public boolean ADD_LABEL_ON_DEBUGGING = true;
  public boolean ADD_LABEL_ON_UNIT_TEST_PASSED = true;
  public boolean ADD_LABEL_ON_UNIT_TEST_FAILED = true;


  public boolean SHOW_CHANGES_ONLY = false;

  public static LvcsConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(LvcsConfiguration.class);
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public String getComponentName() {
    return "LvcsConfiguration";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

}
