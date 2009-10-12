/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.descriptors.impl;

import com.intellij.util.descriptors.CustomConfigFileSet;
import com.intellij.util.descriptors.CustomConfigFile;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author nik
 */
public class CustomConfigFileSetImpl implements CustomConfigFileSet {
  @NonNls private static final String ELEMENT_NAME = "deploymentDescriptor";
  @NonNls private static final String URL_ATTRIBUTE = "url";
  @NonNls private static final String OPTION_ELEMENT_NAME = "option";
  @NonNls private static final String DEFAULT_DIR_OPTION = "DEFAULT_DIR";
  @NonNls private static final String NAME_ATTRIBUTE = "name";
  @NonNls private static final String VALUE_ATTRIBUTE = "value";

  private final List<CustomConfigFile> myDescriptors = new ArrayList<CustomConfigFile>();

  public void add(CustomConfigFile descriptor) {
    myDescriptors.add(descriptor);
  }

  public void remove(CustomConfigFile descriptor) {
    myDescriptors.remove(descriptor);
  }

  public CustomConfigFile[] getConfigFiles() {
    return myDescriptors.toArray(new CustomConfigFile[myDescriptors.size()]);
  }

  public void setConfigFiles(final Collection<CustomConfigFile> descriptors) {
    myDescriptors.clear();
    myDescriptors.addAll(descriptors);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myDescriptors.clear();
    List<Element> descriptors = element.getChildren(ELEMENT_NAME);
    for (Element descriptor : descriptors) {
      String url = descriptor.getAttributeValue(URL_ATTRIBUTE);
      String directory = getDefaultDirOption(descriptor);
      if (directory != null) {
        myDescriptors.add(new CustomConfigFile(url, directory));
      }
    }
  }

  @Nullable
  private static String getDefaultDirOption(final Element descriptor) {
    List<Element> options = descriptor.getChildren(OPTION_ELEMENT_NAME);
    for (Element option : options) {
      if (DEFAULT_DIR_OPTION.equals(option.getAttributeValue(NAME_ATTRIBUTE))) {
        return option.getAttributeValue(VALUE_ATTRIBUTE);
      }
    }
    return null;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (CustomConfigFile descriptor : myDescriptors) {
      final Element child = new Element(ELEMENT_NAME);
      child.setAttribute(URL_ATTRIBUTE, descriptor.getUrl());
      final Element option = new Element(OPTION_ELEMENT_NAME);
      option.setAttribute(NAME_ATTRIBUTE, DEFAULT_DIR_OPTION);
      option.setAttribute(VALUE_ATTRIBUTE, descriptor.getOutputDirectoryPath());
      child.addContent(option);
      element.addContent(child);
    }
  }
}
