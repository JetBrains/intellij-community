/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    //noinspection HardCodedStringLiteral
    return "LvcsConfiguration";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

}
