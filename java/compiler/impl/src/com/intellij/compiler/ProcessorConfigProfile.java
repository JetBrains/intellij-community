/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.compiler;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 5/25/12
 */
public final class ProcessorConfigProfile implements AnnotationProcessingConfiguration {
  private static final Comparator<String> ALPHA_COMPARATOR = new Comparator<String>() {
    @Override
    public int compare(String o1, String o2) {
      return o1.compareToIgnoreCase(o2);
    }
  };
  private static final String ENTRY = "entry";
  private static final String NAME = "name";
  private static final String VALUE = "value";
  private static final String ENABLED = "enabled";
  private static final String OPTION = "option";
  private static final String MODULE = "module";

  private String myName = "";
  private boolean myEnabled = false;
  private boolean myObtainProcessorsFromClasspath = true;
  private String myProcessorPath = "";
  private final Set<String> myProcessors = new HashSet<String>(); // empty list means all discovered
  private final Map<String, String> myProcessorOptions = new HashMap<String, String>(); // key=value map of options
  @Nullable
  private String myGeneratedSourcesDirectoryName = null; // null means 'auto'
  private final Set<String> myModuleNames = new HashSet<String>();

  public ProcessorConfigProfile(String name) {
    myName = name;
  }

  public ProcessorConfigProfile(ProcessorConfigProfile profile) {
    initFrom(profile);
  }

  public void readExternal(Element element) {
    setName(element.getAttributeValue(NAME, ""));
    setEnabled(Boolean.valueOf(element.getAttributeValue(ENABLED, "false")));

    final Element srcOutput = element.getChild("sourceOutputDir");
    setGeneratedSourcesDirectoryName(srcOutput != null ? srcOutput.getAttributeValue(NAME) : null);

    clearProcessorOptions();
    for (Object optionElement : element.getChildren(OPTION)) {
      final Element elem = (Element)optionElement;
      final String key = elem.getAttributeValue(NAME);
      final String value = elem.getAttributeValue(VALUE);
      if (!StringUtil.isEmptyOrSpaces(key) && value != null) {
        setOption(key, value);
      }
    }

    clearProcessors();
    for (Object procElement : element.getChildren("processor")) {
      final String name = ((Element)procElement).getAttributeValue(NAME);
      if (StringUtil.isEmptyOrSpaces(name)) {
        addProcessor(name);
      }
    }

    final Element pathElement = element.getChild("processorPath");
    if (pathElement != null) {
      setObtainProcessorsFromClasspath(Boolean.parseBoolean(pathElement.getAttributeValue("useClasspath", "true")));
      final StringBuilder pathBuilder = new StringBuilder();
      for (Object entry : pathElement.getChildren(ENTRY)) {
        final String path = ((Element)entry).getAttributeValue(NAME);
        if (!StringUtil.isEmptyOrSpaces(path)) {
          if (pathBuilder.length() > 0) {
            pathBuilder.append(File.pathSeparator);
          }
          pathBuilder.append(FileUtil.toSystemDependentName(path));
        }
      }
      setProcessorPath(pathBuilder.toString());
    }

    clearModuleNames();
    for (Object moduleElement : element.getChildren(MODULE)) {
      final String name = ((Element)moduleElement).getAttributeValue(NAME);
      if (!StringUtil.isEmptyOrSpaces(name)) {
        addModuleName(name);
      }
    }
  }

  public void writeExternal(final Element element) {
    element.setAttribute(NAME, getName());
    element.setAttribute(ENABLED, Boolean.toString(isEnabled()));

    final String srcDirName = getGeneratedSourcesDirectoryName();
    if (srcDirName != null) {
      addChild(element, "sourceOutputDir").setAttribute(NAME, srcDirName);
    }

    final Map<String, String> options = getProcessorOptions();
    if (!options.isEmpty()) {
      final List<String> keys = new ArrayList<String>(options.keySet());
      Collections.sort(keys, ALPHA_COMPARATOR);
      for (String key : keys) {
        addChild(element, OPTION).setAttribute(NAME, key).setAttribute(VALUE, options.get(key));
      }
    }

    final Set<String> processors = getProcessors();
    if (!processors.isEmpty()) {
      final List<String> processorList = new ArrayList<String>(processors);
      Collections.sort(processorList, ALPHA_COMPARATOR);
      for (String proc : processorList) {
        addChild(element, "processor").setAttribute(NAME, proc);
      }
    }

    final Element pathElement = addChild(element, "processorPath").setAttribute("useClasspath", Boolean.toString(isObtainProcessorsFromClasspath()));
    final String path = getProcessorPath();
    if (!StringUtil.isEmpty(path)) {
      final StringTokenizer tokenizer = new StringTokenizer(path, File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        final String token = tokenizer.nextToken();
        addChild(pathElement, ENTRY).setAttribute(NAME, FileUtil.toSystemIndependentName(token));
      }
    }

    final Set<String> moduleNames = getModuleNames();
    if (!moduleNames.isEmpty()) {
      final List<String> names = new ArrayList<String>(moduleNames);
      Collections.sort(names, ALPHA_COMPARATOR);
      for (String name : names) {
        addChild(element, MODULE).setAttribute(NAME, name);
      }
    }
  }

  public final void initFrom(ProcessorConfigProfile other) {
    myName = other.myName;
    myEnabled = other.myEnabled;
    myObtainProcessorsFromClasspath = other.myObtainProcessorsFromClasspath;
    myProcessorPath = other.myProcessorPath;
    myProcessors.clear();
    myProcessors.addAll(other.myProcessors);
    myProcessorOptions.clear();
    myProcessorOptions.putAll(other.myProcessorOptions);
    myGeneratedSourcesDirectoryName = other.myGeneratedSourcesDirectoryName;
    myModuleNames.clear();
    myModuleNames.addAll(other.myModuleNames);
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  @Override
  @NotNull
  public String getProcessorPath() {
    return myProcessorPath;
  }

  public void setProcessorPath(@Nullable String processorPath) {
    myProcessorPath = processorPath != null? processorPath : "";
  }

  @Override
  public boolean isObtainProcessorsFromClasspath() {
    return myObtainProcessorsFromClasspath;
  }

  public void setObtainProcessorsFromClasspath(boolean value) {
    myObtainProcessorsFromClasspath = value;
  }

  @Override
  @Nullable
  public String getGeneratedSourcesDirectoryName() {
    return myGeneratedSourcesDirectoryName;
  }

  public void setGeneratedSourcesDirectoryName(@Nullable String generatedSourcesDirectoryName) {
    myGeneratedSourcesDirectoryName = generatedSourcesDirectoryName;
  }

  @NotNull
  public Set<String> getModuleNames() {
    return myModuleNames;
  }

  public boolean addModuleName(String name) {
    return myModuleNames.add(name);
  }

  public boolean addModuleNames(Collection<String> names) {
    return myModuleNames.addAll(names);
  }

  public boolean removeModuleName(String name) {
    return myModuleNames.remove(name);
  }

  public boolean removeModuleNames(Collection<String> names) {
    return myModuleNames.removeAll(names);
  }

  public void clearModuleNames() {
    myModuleNames.clear();
  }

  public void clearProcessors() {
    myProcessors.clear();
  }

  public boolean addProcessor(String processor) {
    return myProcessors.add(processor);
  }

  public boolean removeProcessor(String processor) {
    return myProcessors.remove(processor);
  }

  @Override
  @NotNull
  public Set<String> getProcessors() {
    return Collections.unmodifiableSet(myProcessors);
  }

  @Override
  @NotNull
  public Map<String, String> getProcessorOptions() {
    return Collections.unmodifiableMap(myProcessorOptions);
  }

  public String setOption(String key, String value) {
    return myProcessorOptions.put(key, value);
  }

  @Nullable
  public String getOption(String key) {
    return myProcessorOptions.get(key);
  }

  public void clearProcessorOptions() {
    myProcessorOptions.clear();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProcessorConfigProfile profile = (ProcessorConfigProfile)o;

    if (myEnabled != profile.myEnabled) return false;
    if (myObtainProcessorsFromClasspath != profile.myObtainProcessorsFromClasspath) return false;
    if (myGeneratedSourcesDirectoryName != null
        ? !myGeneratedSourcesDirectoryName.equals(profile.myGeneratedSourcesDirectoryName)
        : profile.myGeneratedSourcesDirectoryName != null) {
      return false;
    }
    if (!myModuleNames.equals(profile.myModuleNames)) return false;
    if (!myProcessorOptions.equals(profile.myProcessorOptions)) return false;
    if (myProcessorPath != null ? !myProcessorPath.equals(profile.myProcessorPath) : profile.myProcessorPath != null) return false;
    if (!myProcessors.equals(profile.myProcessors)) return false;
    if (!myName.equals(profile.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myEnabled ? 1 : 0);
    result = 31 * result + (myObtainProcessorsFromClasspath ? 1 : 0);
    result = 31 * result + (myProcessorPath != null ? myProcessorPath.hashCode() : 0);
    result = 31 * result + myProcessors.hashCode();
    result = 31 * result + myProcessorOptions.hashCode();
    result = 31 * result + (myGeneratedSourcesDirectoryName != null ? myGeneratedSourcesDirectoryName.hashCode() : 0);
    result = 31 * result + myModuleNames.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return myName;
  }

  private static Element addChild(Element parent, final String childName) {
    final Element child = new Element(childName);
    parent.addContent(child);
    return child;
  }
}

