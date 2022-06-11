// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import java.io.File;
import java.util.*;

public final class AnnotationProcessorProfileSerializer {
  private static final Comparator<String> ALPHA_COMPARATOR = (o1, o2) -> o1.compareToIgnoreCase(o2);
  private static final String ENTRY = "entry";
  private static final String NAME = "name";
  private static final String VALUE = "value";
  private static final String ENABLED = "enabled";
  private static final String PROC_ONLY = "procOnly";
  private static final String OPTION = "option";
  private static final String MODULE = "module";
  private static final String USE_CLASSPATH = "useClasspath";
  private static final String USE_PROC_MODULE_PATH = "useProcessorModulePath";

  public static void readExternal(ProcessorConfigProfile profile, Element element) {
    profile.setName(element.getAttributeValue(NAME, ""));
    profile.setEnabled(Boolean.parseBoolean(element.getAttributeValue(ENABLED, "false")));
    profile.setProcOnly(Boolean.parseBoolean(element.getAttributeValue(PROC_ONLY, "false")));

    final Element srcOutput = element.getChild("sourceOutputDir");
    final String out = srcOutput != null ? srcOutput.getAttributeValue(NAME) : null;
    profile.setGeneratedSourcesDirectoryName(out != null ? FileUtilRt.toSystemDependentName(out) : null, false);

    final Element srcTestOutput = element.getChild("sourceTestOutputDir");
    final String testOut = srcTestOutput != null ? srcTestOutput.getAttributeValue(NAME) : null;
    profile.setGeneratedSourcesDirectoryName(testOut != null? FileUtilRt.toSystemDependentName(testOut) : null, true);

    final Element isRelativeToContentRoot = element.getChild("outputRelativeToContentRoot");
    if (isRelativeToContentRoot != null) {
      profile.setOutputRelativeToContentRoot(Boolean.parseBoolean(isRelativeToContentRoot.getAttributeValue(VALUE)));
    }

    profile.clearProcessorOptions();
    for (Element optionElement : element.getChildren(OPTION)) {
      final String key = optionElement.getAttributeValue(NAME);
      final String value = optionElement.getAttributeValue(VALUE);
      if (!StringUtil.isEmptyOrSpaces(key) && value != null) {
        profile.setOption(key, value);
      }
    }

    profile.clearProcessors();
    for (Element procElement : element.getChildren("processor")) {
      final String name = procElement.getAttributeValue(NAME);
      if (!StringUtil.isEmptyOrSpaces(name)) {
        profile.addProcessor(name);
      }
    }

    final Element pathElement = element.getChild("processorPath");
    if (pathElement != null) {
      profile.setObtainProcessorsFromClasspath(Boolean.parseBoolean(pathElement.getAttributeValue(USE_CLASSPATH, "true")));
      profile.setUseProcessorModulePath(Boolean.parseBoolean(pathElement.getAttributeValue(USE_PROC_MODULE_PATH, "false")));
      final StringBuilder pathBuilder = new StringBuilder();
      for (Element entry : pathElement.getChildren(ENTRY)) {
        final String path = entry.getAttributeValue(NAME);
        if (!StringUtil.isEmptyOrSpaces(path)) {
          if (pathBuilder.length() > 0) {
            pathBuilder.append(File.pathSeparator);
          }
          pathBuilder.append(FileUtilRt.toSystemDependentName(path));
        }
      }
      profile.setProcessorPath(pathBuilder.toString());
    }

    profile.clearModuleNames();
    for (Element moduleElement : element.getChildren(MODULE)) {
      final String name = moduleElement.getAttributeValue(NAME);
      if (!StringUtil.isEmptyOrSpaces(name)) {
        profile.addModuleName(name);
      }
    }
  }

  public static void writeExternal(@NotNull ProcessorConfigProfile profile, @NotNull Element element) {
    element.setAttribute(NAME, profile.getName());
    if (profile.isEnabled()) {
      element.setAttribute(ENABLED, Boolean.toString(profile.isEnabled()));
    }
    if (profile.isProcOnly()) {
      element.setAttribute(PROC_ONLY, Boolean.toString(profile.isProcOnly()));
    }
    final String srcDirName = profile.getGeneratedSourcesDirectoryName(false);
    if (!StringUtil.equals(ProcessorConfigProfile.DEFAULT_PRODUCTION_DIR_NAME, srcDirName)) {
      addChild(element, "sourceOutputDir").setAttribute(NAME, FileUtilRt.toSystemIndependentName(srcDirName));
    }
    final String testSrcDirName = profile.getGeneratedSourcesDirectoryName(true);
    if (!StringUtil.equals(ProcessorConfigProfile.DEFAULT_TESTS_DIR_NAME, testSrcDirName)) {
      addChild(element, "sourceTestOutputDir").setAttribute(NAME, FileUtilRt.toSystemIndependentName(testSrcDirName));
    }

    if (profile.isOutputRelativeToContentRoot()) {
      addChild(element, "outputRelativeToContentRoot").setAttribute(VALUE, "true");
    }

    final Map<String, String> options = profile.getProcessorOptions();
    if (!options.isEmpty()) {
      final List<String> keys = new ArrayList<>(options.keySet());
      keys.sort(ALPHA_COMPARATOR);
      for (String key : keys) {
        addChild(element, OPTION).setAttribute(NAME, key).setAttribute(VALUE, options.get(key));
      }
    }

    final Set<String> processors = profile.getProcessors();
    if (!processors.isEmpty()) {
      final List<String> processorList = new ArrayList<>(processors);
      for (String proc : processorList) {
        addChild(element, "processor").setAttribute(NAME, proc);
      }
    }


    Element pathElement = null;
    if (!profile.isObtainProcessorsFromClasspath()) {
      pathElement = addChild(element, "processorPath");
      pathElement.setAttribute(USE_CLASSPATH, Boolean.toString(profile.isObtainProcessorsFromClasspath()));
      if (profile.isUseProcessorModulePath()) {
        pathElement.setAttribute(USE_PROC_MODULE_PATH, Boolean.toString(profile.isUseProcessorModulePath()));
      }
    }

    final String path = profile.getProcessorPath();
    if (!StringUtil.isEmpty(path)) {
      if (pathElement == null) {
        pathElement = addChild(element, "processorPath");
        if (profile.isUseProcessorModulePath()) {
          pathElement.setAttribute(USE_PROC_MODULE_PATH, Boolean.toString(profile.isUseProcessorModulePath()));
        }
      }
      final StringTokenizer tokenizer = new StringTokenizer(path, File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        final String token = tokenizer.nextToken();
        addChild(pathElement, ENTRY).setAttribute(NAME, FileUtilRt.toSystemIndependentName(token));
      }
    }

    final Set<String> moduleNames = profile.getModuleNames();
    if (!moduleNames.isEmpty()) {
      final List<String> names = new ArrayList<>(moduleNames);
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
