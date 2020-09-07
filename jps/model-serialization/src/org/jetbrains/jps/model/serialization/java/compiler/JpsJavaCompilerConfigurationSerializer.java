// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.List;

public class JpsJavaCompilerConfigurationSerializer extends JpsProjectExtensionSerializer {
  public static final String EXCLUDE_FROM_COMPILE = "excludeFromCompile";
  public static final String RESOURCE_EXTENSIONS = "resourceExtensions";
  public static final String ANNOTATION_PROCESSING = "annotationProcessing";
  public static final String BYTECODE_TARGET_LEVEL = "bytecodeTargetLevel";
  public static final String WILDCARD_RESOURCE_PATTERNS = "wildcardResourcePatterns";
  public static final String ADD_NOTNULL_ASSERTIONS = "addNotNullAssertions";
  public static final String ENTRY = "entry";
  public static final String NAME = "name";
  public static final String ENABLED = "enabled";
  public static final String MODULE = "module";
  public static final String TARGET_ATTRIBUTE = "target";

  public static final List<String> DEFAULT_WILDCARD_PATTERNS =
    ContainerUtil.immutableList("!?*.java", "!?*.form", "!?*.class", "!?*.groovy", "!?*.scala", "!?*.flex", "!?*.kt", "!?*.clj", "!?*.aj");

  public JpsJavaCompilerConfigurationSerializer() {
    super("compiler.xml", "CompilerConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
    Element addNotNullTag = componentTag.getChild(ADD_NOTNULL_ASSERTIONS);
    if (addNotNullTag != null) {
      configuration.setAddNotNullAssertions(Boolean.parseBoolean(addNotNullTag.getAttributeValue(ENABLED, "true")));
    }

    readExcludes(componentTag.getChild(EXCLUDE_FROM_COMPILE), configuration.getCompilerExcludes());

    Element resourcePatternsTag = componentTag.getChild(WILDCARD_RESOURCE_PATTERNS);
    if (resourcePatternsTag == null) {
      for (String pattern : DEFAULT_WILDCARD_PATTERNS) {
        configuration.addResourcePattern(pattern);
      }
    }
    else {
      for (Element entry : resourcePatternsTag.getChildren(ENTRY)) {
        String pattern = entry.getAttributeValue(NAME);
        if (!StringUtil.isEmpty(pattern)) {
          configuration.addResourcePattern(pattern);
        }
      }
    }

    Element annotationProcessingTag = componentTag.getChild(ANNOTATION_PROCESSING);
    if (annotationProcessingTag != null) {
      List<Element> profiles = JDOMUtil.getChildren(annotationProcessingTag, "profile");
      for (Element profileTag : profiles) {
        boolean isDefault = Boolean.parseBoolean(profileTag.getAttributeValue("default"));
        if (isDefault) {
          AnnotationProcessorProfileSerializer.readExternal(configuration.getDefaultAnnotationProcessingProfile(), profileTag);
        }
        else {
          AnnotationProcessorProfileSerializer.readExternal(configuration.addAnnotationProcessingProfile(), profileTag);
        }
      }
    }

    Element targetLevelTag = componentTag.getChild(BYTECODE_TARGET_LEVEL);
    if (targetLevelTag != null) {
      configuration.setProjectByteCodeTargetLevel(targetLevelTag.getAttributeValue(TARGET_ATTRIBUTE));
      for (Element moduleTag : JDOMUtil.getChildren(targetLevelTag, MODULE)) {
        String moduleName = moduleTag.getAttributeValue(NAME);
        String level = moduleTag.getAttributeValue(TARGET_ATTRIBUTE);
        if (moduleName != null && level != null) {
          configuration.setModuleByteCodeTargetLevel(moduleName, level);
        }
      }
    }
    String compilerId = JDOMExternalizerUtil.readField(componentTag, "DEFAULT_COMPILER");
    if (compilerId != null) {
      configuration.setJavaCompilerId(compilerId);
    }

    String useReleaseOption = JDOMExternalizerUtil.readField(componentTag, "USE_RELEASE_OPTION");
    if (useReleaseOption != null) {
      configuration.setUseReleaseOption(Boolean.parseBoolean(useReleaseOption));
    }
  }

  @Override
  public void loadExtensionWithDefaultSettings(@NotNull JpsProject project) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
    for (String pattern : DEFAULT_WILDCARD_PATTERNS) {
      configuration.addResourcePattern(pattern);
    }
  }

  public static void readExcludes(Element excludeFromCompileTag, JpsCompilerExcludes excludes) {
    if (excludeFromCompileTag != null) {
      for (Element fileTag : JDOMUtil.getChildren(excludeFromCompileTag, "file")) {
        excludes.addExcludedFile(fileTag.getAttributeValue("url"));
      }
      for (Element directoryTag : JDOMUtil.getChildren(excludeFromCompileTag, "directory")) {
        boolean recursively = Boolean.parseBoolean(directoryTag.getAttributeValue("includeSubdirectories"));
        excludes.addExcludedDirectory(directoryTag.getAttributeValue("url"), recursively);
      }
    }
  }
}
