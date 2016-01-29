/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class AnnotationProcessorProfileSerializer {
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

  public static void readExternal(ProcessorConfigProfile profile, Element element) {
    profile.setName(element.getAttributeValue(NAME, ""));
    profile.setEnabled(Boolean.valueOf(element.getAttributeValue(ENABLED, "false")));

    final Element srcOutput = element.getChild("sourceOutputDir");
    final String out = srcOutput != null ? srcOutput.getAttributeValue(NAME) : null;
    profile.setGeneratedSourcesDirectoryName(out != null? FileUtil.toSystemDependentName(out) : null, false);

    final Element srcTestOutput = element.getChild("sourceTestOutputDir");
    final String testOut = srcTestOutput != null ? srcTestOutput.getAttributeValue(NAME) : null;
    profile.setGeneratedSourcesDirectoryName(testOut != null? FileUtil.toSystemDependentName(testOut) : null, true);

    final Element isRelativeToContentRoot = element.getChild("outputRelativeToContentRoot");
    if (isRelativeToContentRoot != null) {
      profile.setOutputRelativeToContentRoot(Boolean.parseBoolean(isRelativeToContentRoot.getAttributeValue(VALUE)));
    }

    profile.clearProcessorOptions();
    for (Object optionElement : element.getChildren(OPTION)) {
      final Element elem = (Element)optionElement;
      final String key = elem.getAttributeValue(NAME);
      final String value = elem.getAttributeValue(VALUE);
      if (!StringUtil.isEmptyOrSpaces(key) && value != null) {
        profile.setOption(key, value);
      }
    }

    profile.clearProcessors();
    for (Object procElement : element.getChildren("processor")) {
      final String name = ((Element)procElement).getAttributeValue(NAME);
      if (!StringUtil.isEmptyOrSpaces(name)) {
        profile.addProcessor(name);
      }
    }

    final Element pathElement = element.getChild("processorPath");
    if (pathElement != null) {
      profile.setObtainProcessorsFromClasspath(Boolean.parseBoolean(pathElement.getAttributeValue("useClasspath", "true")));
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
      profile.setProcessorPath(pathBuilder.toString());
    }

    profile.clearModuleNames();
    for (Object moduleElement : element.getChildren(MODULE)) {
      final String name = ((Element)moduleElement).getAttributeValue(NAME);
      if (!StringUtil.isEmptyOrSpaces(name)) {
        profile.addModuleName(name);
      }
    }
  }

  public static void writeExternal(@NotNull ProcessorConfigProfile profile, @NotNull Element element) {
    element.setAttribute(NAME, profile.getName());
    if (!Registry.is("saving.state.in.new.format.is.allowed", false) || profile.isEnabled()) {
      element.setAttribute(ENABLED, Boolean.toString(profile.isEnabled()));
    }

    final String srcDirName = profile.getGeneratedSourcesDirectoryName(false);
    if (!StringUtil.equals(ProcessorConfigProfile.DEFAULT_PRODUCTION_DIR_NAME, srcDirName)) {
      addChild(element, "sourceOutputDir").setAttribute(NAME, FileUtil.toSystemIndependentName(srcDirName));
    }
    final String testSrcDirName = profile.getGeneratedSourcesDirectoryName(true);
    if (!StringUtil.equals(ProcessorConfigProfile.DEFAULT_TESTS_DIR_NAME, testSrcDirName)) {
      addChild(element, "sourceTestOutputDir").setAttribute(NAME, FileUtil.toSystemIndependentName(testSrcDirName));
    }

    if (profile.isOutputRelativeToContentRoot()) {
      addChild(element, "outputRelativeToContentRoot").setAttribute(VALUE, "true");
    }

    final Map<String, String> options = profile.getProcessorOptions();
    if (!options.isEmpty()) {
      final List<String> keys = new ArrayList<String>(options.keySet());
      Collections.sort(keys, ALPHA_COMPARATOR);
      for (String key : keys) {
        addChild(element, OPTION).setAttribute(NAME, key).setAttribute(VALUE, options.get(key));
      }
    }

    final Set<String> processors = profile.getProcessors();
    if (!processors.isEmpty()) {
      final List<String> processorList = new ArrayList<String>(processors);
      Collections.sort(processorList, ALPHA_COMPARATOR);
      for (String proc : processorList) {
        addChild(element, "processor").setAttribute(NAME, proc);
      }
    }


    Element pathElement = null;
    if (!Registry.is("saving.state.in.new.format.is.allowed", false) || !profile.isObtainProcessorsFromClasspath()) {
      pathElement = addChild(element, "processorPath");
      pathElement.setAttribute("useClasspath", Boolean.toString(profile.isObtainProcessorsFromClasspath()));
    }

    final String path = profile.getProcessorPath();
    if (!StringUtil.isEmpty(path)) {
      if (pathElement == null) {
        pathElement = addChild(element, "processorPath");
      }
      final StringTokenizer tokenizer = new StringTokenizer(path, File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        final String token = tokenizer.nextToken();
        addChild(pathElement, ENTRY).setAttribute(NAME, FileUtil.toSystemIndependentName(token));
      }
    }

    final Set<String> moduleNames = profile.getModuleNames();
    if (!moduleNames.isEmpty()) {
      final List<String> names = new ArrayList<String>(moduleNames);
      Collections.sort(names, ALPHA_COMPARATOR);
      for (String name : names) {
        addChild(element, MODULE).setAttribute(NAME, name);
      }
    }
  }

  private static Element addChild(Element parent, final String childName) {
    final Element child = new Element(childName);
    parent.addContent(child);
    return child;
  }
}
