/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public final class ConfigurationPerRunnerSettings implements JDOMExternalizable {
  private final RunnerInfo myRunnerInfo;
  private final JDOMExternalizable mySettings;

  public ConfigurationPerRunnerSettings(RunnerInfo runnerInfo, JDOMExternalizable settings) {
    myRunnerInfo = runnerInfo;
    mySettings = settings;
  }

  public RunnerInfo getRunnerInfo() {
    return myRunnerInfo;
  }

  public JDOMExternalizable getSettings() {
    return mySettings;
  }

  public void readExternal(Element element) throws InvalidDataException {
    if (mySettings != null) {
      mySettings.readExternal(element);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (mySettings != null) {
      mySettings.writeExternal(element);
    }
  }
}