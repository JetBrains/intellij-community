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
package org.jetbrains.jps.model.serialization.java;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.JpsUrlList;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.java.compiler.*;
import org.jetbrains.jps.model.serialization.library.JpsLibraryPropertiesSerializer;
import org.jetbrains.jps.model.serialization.library.JpsLibraryRootTypeSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JpsJavaModelSerializerExtension extends JpsModelSerializerExtension {
  public static final String EXPORTED_ATTRIBUTE = "exported";
  public static final String SCOPE_ATTRIBUTE = "scope";
  public static final String OUTPUT_TAG = "output";
  public static final String URL_ATTRIBUTE = "url";
  public static final String LANGUAGE_LEVEL_ATTRIBUTE = "languageLevel";
  public static final String EXPLODED_TAG = "exploded";
  public static final String EXCLUDE_EXPLODED_TAG = "exclude-exploded";
  public static final String TEST_OUTPUT_TAG = "output-test";
  public static final String INHERIT_COMPILER_OUTPUT_ATTRIBUTE = "inherit-compiler-output";
  public static final String EXCLUDE_OUTPUT_TAG = "exclude-output";
  public static final String ANNOTATION_PATHS_TAG = "annotation-paths";
  public static final String JAVADOC_PATHS_TAG = "javadoc-paths";
  public static final String MODULE_LANGUAGE_LEVEL_ATTRIBUTE = "LANGUAGE_LEVEL";
  public static final String ROOT_TAG = "root";
  public static final String RELATIVE_OUTPUT_PATH_ATTRIBUTE = "relativeOutputPath";
  public static final String IS_GENERATED_ATTRIBUTE = "generated";
  public static final JavaSourceRootPropertiesSerializer JAVA_SOURCE_ROOT_PROPERTIES_SERIALIZER =
    new JavaSourceRootPropertiesSerializer(JavaSourceRootType.SOURCE, JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID);
  public static final String JAVA_RESOURCE_ROOT_ID = "java-resource";
  public static final String JAVA_TEST_RESOURCE_ROOT_ID = "java-test-resource";

  @Override
  public void loadRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
    loadExplodedDirectoryExtension(module, rootModel);
    loadJavaModuleExtension(module, rootModel);
  }

  @Override
  public void saveRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
    saveExplodedDirectoryExtension(module, rootModel);
    saveJavaModuleExtension(module, rootModel);
  }

  @Override
  public void loadModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    Element testModuleProperties = JDomSerializationUtil.findComponent(rootElement, "TestModuleProperties");
    if (testModuleProperties != null) {
      String productionModuleName = testModuleProperties.getAttributeValue("production-module");
      if (productionModuleName != null) {
        getService().setTestModuleProperties(module, JpsElementFactory.getInstance().createModuleReference(productionModuleName));
      }
    }
  }

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Arrays.asList(new JavaProjectExtensionSerializer(),
                         new JpsJavaCompilerConfigurationSerializer(),
                         new JpsValidationSerializer(),
                         new JpsJavaCompilerNotNullableSerializer(),
                         new JpsCompilerValidationExcludeSerializer(),
                         new JpsJavaCompilerWorkspaceConfigurationSerializer(),
                         new JpsJavaCompilerOptionsSerializer("JavacSettings", "Javac"),
                         new JpsEclipseCompilerOptionsSerializer("EclipseCompilerSettings", "Eclipse"),
                         new RmicCompilerOptionsSerializer("RmicSettings", "Rmic"));
  }

  @NotNull
  @Override
  public List<? extends JpsModuleSourceRootPropertiesSerializer<?>> getModuleSourceRootPropertiesSerializers() {
    return Arrays.asList(JAVA_SOURCE_ROOT_PROPERTIES_SERIALIZER,
                         new JavaSourceRootPropertiesSerializer(JavaSourceRootType.TEST_SOURCE, JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID),
                         new JavaResourceRootPropertiesSerializer(JavaResourceRootType.RESOURCE, JAVA_RESOURCE_ROOT_ID),
                         new JavaResourceRootPropertiesSerializer(JavaResourceRootType.TEST_RESOURCE, JAVA_TEST_RESOURCE_ROOT_ID));
  }

  @Override
  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element entry) {
    boolean exported = entry.getAttributeValue(EXPORTED_ATTRIBUTE) != null;
    String scopeName = entry.getAttributeValue(SCOPE_ATTRIBUTE);
    JpsJavaDependencyScope scope;
    try {
      scope = scopeName != null ? JpsJavaDependencyScope.valueOf(scopeName) : JpsJavaDependencyScope.COMPILE;
    }
    catch (IllegalArgumentException e) {
      scope = JpsJavaDependencyScope.COMPILE;
    }

    final JpsJavaDependencyExtension extension = getService().getOrCreateDependencyExtension(dependency);
    extension.setExported(exported);
    extension.setScope(scope);
  }

  @Override
  public void saveModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    JpsJavaDependencyExtension extension = getService().getDependencyExtension(dependency);
    if (extension != null) {
      if (extension.isExported()) {
        orderEntry.setAttribute(EXPORTED_ATTRIBUTE, "");
      }
      JpsJavaDependencyScope scope = extension.getScope();
      if (scope != JpsJavaDependencyScope.COMPILE) {
        orderEntry.setAttribute(SCOPE_ATTRIBUTE, scope.name());
      }
    }
  }

  @Override
  public List<JpsLibraryRootTypeSerializer> getLibraryRootTypeSerializers() {
    return Arrays.asList(new JpsLibraryRootTypeSerializer("JAVADOC", JpsOrderRootType.DOCUMENTATION, true),
                         new JpsLibraryRootTypeSerializer("ANNOTATIONS", JpsAnnotationRootType.INSTANCE, false),
                         new JpsLibraryRootTypeSerializer("NATIVE", JpsNativeLibraryRootType.INSTANCE, false));
  }

  @NotNull
  @Override
  public List<JpsLibraryRootTypeSerializer> getSdkRootTypeSerializers() {
    return Arrays.asList(new JpsLibraryRootTypeSerializer("javadocPath", JpsOrderRootType.DOCUMENTATION, true),
                         new JpsLibraryRootTypeSerializer("annotationsPath", JpsAnnotationRootType.INSTANCE, true));
  }

  @NotNull
  @Override
  public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
    return Arrays.asList(new JpsModuleOutputPackagingElementSerializer(),
                         new JpsTestModuleOutputPackagingElementSerializer(),
                         new JpsModuleSourcePackagingElementSerializer());
  }

  @Override
  @NotNull
  public List<? extends JpsLibraryPropertiesSerializer<?>> getLibraryPropertiesSerializers() {
    return Collections.singletonList(new JpsRepositoryLibraryPropertiesSerializer());
  }

  private static void loadExplodedDirectoryExtension(JpsModule module, Element rootModelComponent) {
    final Element exploded = rootModelComponent.getChild(EXPLODED_TAG);
    if (exploded != null) {
      final ExplodedDirectoryModuleExtension extension = getService().getOrCreateExplodedDirectoryExtension(module);
      extension.setExcludeExploded(rootModelComponent.getChild(EXCLUDE_EXPLODED_TAG) != null);
      extension.setExplodedUrl(exploded.getAttributeValue(URL_ATTRIBUTE));
    }
  }

  private static void saveExplodedDirectoryExtension(JpsModule module, Element rootModelElement) {
    ExplodedDirectoryModuleExtension extension = getService().getExplodedDirectoryExtension(module);
    if (extension != null) {
      if (extension.isExcludeExploded()) {
        rootModelElement.addContent(0, new Element(EXCLUDE_EXPLODED_TAG));
      }
      rootModelElement.addContent(0, new Element(EXPLODED_TAG).setAttribute(URL_ATTRIBUTE, extension.getExplodedUrl()));
    }
  }

  private static void loadJavaModuleExtension(JpsModule module, Element rootModelComponent) {
    final JpsJavaModuleExtension extension = getService().getOrCreateModuleExtension(module);
    final Element outputTag = rootModelComponent.getChild(OUTPUT_TAG);
    String outputUrl = outputTag != null ? outputTag.getAttributeValue(URL_ATTRIBUTE) : null;
    extension.setOutputUrl(outputUrl);
    final Element testOutputTag = rootModelComponent.getChild(TEST_OUTPUT_TAG);
    String testOutputUrl = testOutputTag != null ? testOutputTag.getAttributeValue(URL_ATTRIBUTE) : null;
    extension.setTestOutputUrl(StringUtil.isEmpty(testOutputUrl) ? outputUrl : testOutputUrl);

    extension.setInheritOutput(Boolean.parseBoolean(rootModelComponent.getAttributeValue(INHERIT_COMPILER_OUTPUT_ATTRIBUTE)));
    extension.setExcludeOutput(rootModelComponent.getChild(EXCLUDE_OUTPUT_TAG) != null);

    final String languageLevel = rootModelComponent.getAttributeValue(MODULE_LANGUAGE_LEVEL_ATTRIBUTE);
    if (languageLevel != null) {
      extension.setLanguageLevel(readLanguageLevel(languageLevel, null));
    }

    loadAdditionalRoots(rootModelComponent, ANNOTATION_PATHS_TAG, extension.getAnnotationRoots());
    loadAdditionalRoots(rootModelComponent, JAVADOC_PATHS_TAG, extension.getJavadocRoots());
  }

  private static void saveJavaModuleExtension(JpsModule module, Element rootModelComponent) {
    JpsJavaModuleExtension extension = getService().getModuleExtension(module);
    if (extension == null) return;
    if (extension.isExcludeOutput()) {
      rootModelComponent.addContent(0, new Element(EXCLUDE_OUTPUT_TAG));
    }

    String testOutputUrl = extension.getTestOutputUrl();
    if (testOutputUrl != null) {
      rootModelComponent.addContent(0, new Element(TEST_OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, testOutputUrl));
    }

    String outputUrl = extension.getOutputUrl();
    if (outputUrl != null) {
      rootModelComponent.addContent(0, new Element(OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, outputUrl));
    }

    LanguageLevel languageLevel = extension.getLanguageLevel();
    if (languageLevel != null) {
      rootModelComponent.setAttribute(MODULE_LANGUAGE_LEVEL_ATTRIBUTE, languageLevel.name());
    }

    if (extension.isInheritOutput()) {
      rootModelComponent.setAttribute(INHERIT_COMPILER_OUTPUT_ATTRIBUTE, "true");
    }

    saveAdditionalRoots(rootModelComponent, ANNOTATION_PATHS_TAG, extension.getAnnotationRoots());
    saveAdditionalRoots(rootModelComponent, JAVADOC_PATHS_TAG, extension.getJavadocRoots());
  }

  private static void loadAdditionalRoots(Element rootModelComponent, final String rootsTagName, final JpsUrlList result) {
    final Element roots = rootModelComponent.getChild(rootsTagName);
    for (Element root : JDOMUtil.getChildren(roots, ROOT_TAG)) {
      result.addUrl(root.getAttributeValue(URL_ATTRIBUTE));
    }
  }

  private static void saveAdditionalRoots(Element rootModelComponent, final String rootsTagName, final JpsUrlList list) {
    List<String> urls = list.getUrls();
    if (!urls.isEmpty()) {
      Element roots = new Element(rootsTagName);
      for (String url : urls) {
        roots.addContent(new Element(ROOT_TAG).setAttribute(URL_ATTRIBUTE, url));
      }
      rootModelComponent.addContent(roots);
    }
  }

  private static JpsJavaExtensionService getService() {
    return JpsJavaExtensionService.getInstance();
  }

  private static class JpsModuleOutputPackagingElementSerializer
    extends JpsPackagingElementSerializer<JpsProductionModuleOutputPackagingElement> {
    private JpsModuleOutputPackagingElementSerializer() {
      super("module-output", JpsProductionModuleOutputPackagingElement.class);
    }

    @Override
    public JpsProductionModuleOutputPackagingElement load(Element element) {
      JpsModuleReference reference = JpsElementFactory.getInstance().createModuleReference(element.getAttributeValue("name"));
      return getService().createProductionModuleOutput(reference);
    }

    @Override
    public void save(JpsProductionModuleOutputPackagingElement element, Element tag) {
      tag.setAttribute("name", element.getModuleReference().getModuleName());
    }
  }

  private static class JpsModuleSourcePackagingElementSerializer
    extends JpsPackagingElementSerializer<JpsProductionModuleSourcePackagingElement> {
    private JpsModuleSourcePackagingElementSerializer() {
      super("module-source", JpsProductionModuleSourcePackagingElement.class);
    }

    @Override
    public JpsProductionModuleSourcePackagingElement load(Element element) {
      JpsModuleReference reference = JpsElementFactory.getInstance().createModuleReference(element.getAttributeValue("name"));
      return getService().createProductionModuleSource(reference);
    }

    @Override
    public void save(JpsProductionModuleSourcePackagingElement element, Element tag) {
      tag.setAttribute("name", element.getModuleReference().getModuleName());
    }
  }

  private static class JpsTestModuleOutputPackagingElementSerializer extends JpsPackagingElementSerializer<JpsTestModuleOutputPackagingElement> {
    private JpsTestModuleOutputPackagingElementSerializer() {
      super("module-test-output", JpsTestModuleOutputPackagingElement.class);
    }

    @Override
    public JpsTestModuleOutputPackagingElement load(Element element) {
      JpsModuleReference reference = JpsElementFactory.getInstance().createModuleReference(element.getAttributeValue("name"));
      return getService().createTestModuleOutput(reference);
    }

    @Override
    public void save(JpsTestModuleOutputPackagingElement element, Element tag) {
      tag.setAttribute("name", element.getModuleReference().getModuleName());
    }
  }

  private static LanguageLevel readLanguageLevel(String level, LanguageLevel defaultLevel) {
    for (LanguageLevel languageLevel : LanguageLevel.values()) {
      if (level.equals(languageLevel.name())) {
        return languageLevel;
      }
    }
    return defaultLevel;
  }

  private static class JavaProjectExtensionSerializer extends JpsProjectExtensionSerializer {
    JavaProjectExtensionSerializer() {
      super(null, "ProjectRootManager");
    }

    @Override
    public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      JpsJavaProjectExtension extension = getService().getOrCreateProjectExtension(project);
      final Element output = componentTag.getChild(OUTPUT_TAG);
      if (output != null) {
        String url = output.getAttributeValue(URL_ATTRIBUTE);
        if (url != null) {
          extension.setOutputUrl(url);
        }
      }
      String languageLevel = componentTag.getAttributeValue(LANGUAGE_LEVEL_ATTRIBUTE);
      if (languageLevel != null) {
        extension.setLanguageLevel(readLanguageLevel(languageLevel, LanguageLevel.HIGHEST));
      }
    }

    @Override
    public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      JpsJavaProjectExtension extension = getService().getProjectExtension(project);
      if (extension == null) return;

      String outputUrl = extension.getOutputUrl();
      if (outputUrl != null) {
        componentTag.addContent(new Element(OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, outputUrl));
      }
      LanguageLevel level = extension.getLanguageLevel();
      componentTag.setAttribute(LANGUAGE_LEVEL_ATTRIBUTE, level.name());
    }
  }

  private static class JavaSourceRootPropertiesSerializer extends JpsModuleSourceRootPropertiesSerializer<JavaSourceRootProperties> {
    private JavaSourceRootPropertiesSerializer(JpsModuleSourceRootType<JavaSourceRootProperties> type, String typeId) {
      super(type, typeId);
    }

    @Override
    public JavaSourceRootProperties loadProperties(@NotNull Element sourceRootTag) {
      String packagePrefix = StringUtil.notNullize(sourceRootTag.getAttributeValue(JpsModuleRootModelSerializer.PACKAGE_PREFIX_ATTRIBUTE));
      boolean isGenerated = Boolean.parseBoolean(sourceRootTag.getAttributeValue(IS_GENERATED_ATTRIBUTE));
      return getService().createSourceRootProperties(packagePrefix, isGenerated);
    }

    @Override
    public void saveProperties(@NotNull JavaSourceRootProperties properties, @NotNull Element sourceRootTag) {
      String isTestSource = Boolean.toString(getType().equals(JavaSourceRootType.TEST_SOURCE));
      sourceRootTag.setAttribute(JpsModuleRootModelSerializer.IS_TEST_SOURCE_ATTRIBUTE, isTestSource);
      String packagePrefix = properties.getPackagePrefix();
      if (!packagePrefix.isEmpty()) {
        sourceRootTag.setAttribute(JpsModuleRootModelSerializer.PACKAGE_PREFIX_ATTRIBUTE, packagePrefix);
      }
      if (properties.isForGeneratedSources()) {
        sourceRootTag.setAttribute(IS_GENERATED_ATTRIBUTE, Boolean.TRUE.toString());
      }
    }
  }

  private static class JavaResourceRootPropertiesSerializer extends JpsModuleSourceRootPropertiesSerializer<JavaResourceRootProperties> {
    private JavaResourceRootPropertiesSerializer(JpsModuleSourceRootType<JavaResourceRootProperties> type, String typeId) {
      super(type, typeId);
    }

    @Override
    public JavaResourceRootProperties loadProperties(@NotNull Element sourceRootTag) {
      String relativeOutputPath = StringUtil.notNullize(sourceRootTag.getAttributeValue(RELATIVE_OUTPUT_PATH_ATTRIBUTE));
      boolean isGenerated = Boolean.parseBoolean(sourceRootTag.getAttributeValue(IS_GENERATED_ATTRIBUTE));
      return getService().createResourceRootProperties(relativeOutputPath, isGenerated);
    }

    @Override
    public void saveProperties(@NotNull JavaResourceRootProperties properties, @NotNull Element sourceRootTag) {
      String relativeOutputPath = properties.getRelativeOutputPath();
      if (!relativeOutputPath.isEmpty()) {
        sourceRootTag.setAttribute(RELATIVE_OUTPUT_PATH_ATTRIBUTE, relativeOutputPath);
      }
      if (properties.isForGeneratedSources()) {
        sourceRootTag.setAttribute(IS_GENERATED_ATTRIBUTE, Boolean.TRUE.toString());
      }
    }
  }

  private static class JpsRepositoryLibraryPropertiesSerializer extends JpsLibraryPropertiesSerializer<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> {
    private static final String MAVEN_ID_ATTRIBUTE = "maven-id";
    private static final String INCLUDE_TRANSITIVE_DEPS_ATTRIBUTE = "include-transitive-deps";
    private static final String EXCLUDE_TAG = "exclude";
    private static final String DEPENDENCY_TAG = "dependency";

    JpsRepositoryLibraryPropertiesSerializer() {
      super(JpsRepositoryLibraryType.INSTANCE, JpsRepositoryLibraryType.INSTANCE.getTypeId());
    }

    @Override
    public JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor> loadProperties(@Nullable Element elem) {
      return JpsElementFactory.getInstance().createSimpleElement(loadDescriptor(elem));
    }

    @NotNull
    private static JpsMavenRepositoryLibraryDescriptor loadDescriptor(@Nullable Element elem) {
      if (elem == null) return new JpsMavenRepositoryLibraryDescriptor(null);

      boolean includeTransitiveDependencies = Boolean.parseBoolean(elem.getAttributeValue(INCLUDE_TRANSITIVE_DEPS_ATTRIBUTE, "true"));
      Element excludeTag = elem.getChild(EXCLUDE_TAG);
      List<Element> dependencyTags = excludeTag != null ? excludeTag.getChildren(DEPENDENCY_TAG) : Collections.emptyList();
      List<String> excludedDependencies = ContainerUtil.map(dependencyTags, it -> it.getAttributeValue(MAVEN_ID_ATTRIBUTE));
      return new JpsMavenRepositoryLibraryDescriptor(elem.getAttributeValue(MAVEN_ID_ATTRIBUTE, (String)null),
                                                     includeTransitiveDependencies, excludedDependencies);
    }

    @Override
    public void saveProperties(JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor> properties, Element element) {
      final String mavenId = properties.getData().getMavenId();
      if (mavenId != null) {
        element.setAttribute(MAVEN_ID_ATTRIBUTE, mavenId);
      }
      List<String> excludedDependencies = properties.getData().getExcludedDependencies();
      if (!excludedDependencies.isEmpty()) {
        Element excludeTag = new Element(EXCLUDE_TAG);
        element.addContent(excludeTag);
        for (String dependency : excludedDependencies) {
          excludeTag.addContent(new Element(DEPENDENCY_TAG).setAttribute(MAVEN_ID_ATTRIBUTE, dependency));
        }
      }
    }
  }
}
