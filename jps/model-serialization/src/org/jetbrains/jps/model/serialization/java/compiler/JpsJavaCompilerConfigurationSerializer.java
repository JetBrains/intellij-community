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

import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
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
    Arrays.asList("!?*.java", "!?*.form", "!?*.class", "!?*.groovy", "!?*.scala", "!?*.flex", "!?*.kt", "!?*.clj", "!?*.aj");

  public JpsJavaCompilerConfigurationSerializer() {
    super("compiler.xml", "CompilerConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
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
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
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

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
  }
}
