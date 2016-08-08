/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection.impl.exclude;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ExcludesConfigurationState {
  private List<String> myFrameworkTypes = new ArrayList<>();
  private List<ExcludedFileState> myFiles = new ArrayList<>();
  private boolean myDetectionEnabled = true;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag = "type", elementValueAttribute = "id")
  public List<String> getFrameworkTypes() {
    return myFrameworkTypes;
  }

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public List<ExcludedFileState> getFiles() {
    return myFiles;
  }

  @Attribute("detection-enabled")
  public boolean isDetectionEnabled() {
    return myDetectionEnabled;
  }

  public void setDetectionEnabled(boolean detectionEnabled) {
    myDetectionEnabled = detectionEnabled;
  }

  public void setFrameworkTypes(List<String> frameworkTypes) {
    myFrameworkTypes = frameworkTypes;
  }

  public void setFiles(List<ExcludedFileState> files) {
    myFiles = files;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ExcludesConfigurationState)) return false;

    ExcludesConfigurationState state = (ExcludesConfigurationState)o;
    return myDetectionEnabled == state.myDetectionEnabled && Comparing.haveEqualElements(myFiles, state.myFiles)
           && Comparing.haveEqualElements(myFrameworkTypes, state.myFrameworkTypes);
  }

  @Override
  public int hashCode() {
    return 31 * myFrameworkTypes.hashCode() + myFiles.hashCode() + (myDetectionEnabled ? 1 : 0);
  }
}
