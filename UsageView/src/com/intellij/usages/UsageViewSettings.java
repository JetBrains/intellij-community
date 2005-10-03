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
package com.intellij.usages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.io.File;

public class UsageViewSettings implements JDOMExternalizable, ApplicationComponent{
  public String EXPORT_FILE_NAME;
  public boolean IS_EXPANDED = false;
  public boolean IS_SHOW_PACKAGES = true;
  public boolean IS_SHOW_METHODS = false;
  public boolean IS_AUTOSCROLL_TO_SOURCE = false;
  public boolean IS_FILTER_DUPLICATED_LINE = false;
  public boolean IS_SHOW_MODULES = false;

  public static UsageViewSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(UsageViewSettings.class);
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public String getComponentName() {
    return "UsageViewSettings";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean isExpanded() {
    return IS_EXPANDED;
  }

  public void setExpanded(boolean val) {
    IS_EXPANDED = val;
  }

  public boolean isShowPackages() {
    return IS_SHOW_PACKAGES;
  }

  public void setShowPackages(boolean val) {
    IS_SHOW_PACKAGES = val;
  }

  public boolean isShowMethods() {
    return IS_SHOW_METHODS;
  }

  public boolean isShowModules() {
    return IS_SHOW_MODULES;
  }

  public void setShowMethods(boolean val) {
    IS_SHOW_METHODS = val;
  }

  public void setShowModules(boolean val) {
    IS_SHOW_MODULES = val;
  }

  public boolean isFilterDuplicatedLine() {
    return IS_FILTER_DUPLICATED_LINE;
  }

  public void setFilterDuplicatedLine(boolean val) {
    IS_FILTER_DUPLICATED_LINE = val;
  }

  public String getExportFileName() {
    return EXPORT_FILE_NAME != null ? EXPORT_FILE_NAME.replace('/', File.separatorChar) : null;
  }

  public void setExportFileName(String s) {
    if (s != null){
      s = s.replace(File.separatorChar, '/');
    }
    EXPORT_FILE_NAME = s;
  }

}
