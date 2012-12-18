package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

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

    Element excludeFromCompileTag = componentTag.getChild(EXCLUDE_FROM_COMPILE);
    if (excludeFromCompileTag != null) {
      for (Element fileTag : JDOMUtil.getChildren(excludeFromCompileTag, "file")) {
        configuration.getCompilerExcludes().addExcludedFile(fileTag.getAttributeValue("url"));
      }
      for (Element directoryTag : JDOMUtil.getChildren(excludeFromCompileTag, "directory")) {
        boolean recursively = Boolean.parseBoolean(directoryTag.getAttributeValue("includeSubdirectories"));
        configuration.getCompilerExcludes().addExcludedDirectory(directoryTag.getAttributeValue("url"), recursively);
      }
    }

    Element resourcePatternsTag = componentTag.getChild(WILDCARD_RESOURCE_PATTERNS);
    for (Element entry : JDOMUtil.getChildren(resourcePatternsTag, ENTRY)) {
      String pattern = entry.getAttributeValue(NAME);
      if (!StringUtil.isEmpty(pattern)) {
        configuration.addResourcePattern(pattern);
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
  }

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
  }
}
