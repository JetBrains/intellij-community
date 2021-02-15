// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.module;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.JpsJavaSdkTypeWrapper;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsPathMapper;
import org.jetbrains.jps.model.serialization.impl.JpsSerializationFormatException;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;

import static com.intellij.openapi.util.JDOMUtil.getChildren;

public final class JpsModuleRootModelSerializer {
  private static final Logger LOG = Logger.getInstance(JpsModuleRootModelSerializer.class);
  public static final String URL_ATTRIBUTE = "url";
  public static final String CONTENT_TAG = "content";
  public static final String SOURCE_FOLDER_TAG = "sourceFolder";
  public static final String PACKAGE_PREFIX_ATTRIBUTE = "packagePrefix";
  public static final String IS_TEST_SOURCE_ATTRIBUTE = "isTestSource";
  public static final String EXCLUDE_FOLDER_TAG = "excludeFolder";
  public static final String EXCLUDE_PATTERN_TAG = "excludePattern";
  public static final String EXCLUDE_PATTERN_ATTRIBUTE = "pattern";
  public static final String ORDER_ENTRY_TAG = "orderEntry";
  public static final String TYPE_ATTRIBUTE = "type";
  public static final String SOURCE_FOLDER_TYPE = "sourceFolder";
  public static final String JDK_TYPE = "jdk";
  public static final String JDK_NAME_ATTRIBUTE = "jdkName";
  public static final String JDK_TYPE_ATTRIBUTE = "jdkType";
  public static final String INHERITED_JDK_TYPE = "inheritedJdk";
  public static final String LIBRARY_TYPE = "library";
  public static final String NAME_ATTRIBUTE = "name";
  public static final String LEVEL_ATTRIBUTE = "level";
  public static final String LIBRARY_TAG = "library";
  public static final String MODULE_LIBRARY_TYPE = "module-library";
  public static final String MODULE_TYPE = "module";
  public static final String MODULE_NAME_ATTRIBUTE = "module-name";
  public static final String SOURCE_ROOT_TYPE_ATTRIBUTE = "type";
  public static final String JAVA_SOURCE_ROOT_TYPE_ID = "java-source";
  public static final String JAVA_TEST_ROOT_TYPE_ID = "java-test";
  private static final String GENERATED_LIBRARY_NAME_PREFIX = "#";

  public static void loadRootModel(JpsModule module, @Nullable Element rootModelComponent, @Nullable JpsSdkType<?> projectSdkType, @NotNull JpsPathMapper pathMapper) {
    if (rootModelComponent == null) return;

    for (Element contentElement : getChildren(rootModelComponent, CONTENT_TAG)) {
      final String url = getRequiredAttribute(contentElement, URL_ATTRIBUTE);
      module.getContentRootsList().addUrl(url);
      for (Element sourceElement : getChildren(contentElement, SOURCE_FOLDER_TAG)) {
        module.addSourceRoot(loadSourceRoot(sourceElement));
      }
      for (Element excludeElement : getChildren(contentElement, EXCLUDE_FOLDER_TAG)) {
        module.getExcludeRootsList().addUrl(getRequiredAttribute(excludeElement, URL_ATTRIBUTE));
      }
      for (Element excludePatternElement : getChildren(contentElement, EXCLUDE_PATTERN_TAG)) {
        module.addExcludePattern(url, getRequiredAttribute(excludePatternElement, EXCLUDE_PATTERN_ATTRIBUTE));
      }
    }

    final JpsDependenciesList dependenciesList = module.getDependenciesList();
    dependenciesList.clear();
    final JpsElementFactory elementFactory = JpsElementFactory.getInstance();
    UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    boolean moduleSourceAdded = false;
    for (Element orderEntry : getChildren(rootModelComponent, ORDER_ENTRY_TAG)) {
      String type = orderEntry.getAttributeValue(TYPE_ATTRIBUTE);
      if (SOURCE_FOLDER_TYPE.equals(type)) {
        dependenciesList.addModuleSourceDependency();
        moduleSourceAdded = true;
      }
      else if (JDK_TYPE.equals(type)) {
        String sdkName = getRequiredAttribute(orderEntry, JDK_NAME_ATTRIBUTE);
        String sdkTypeId = orderEntry.getAttributeValue(JDK_TYPE_ATTRIBUTE);
        final JpsSdkType<?> sdkType = JpsSdkTableSerializer.getSdkType(sdkTypeId);
        dependenciesList.addSdkDependency(sdkType);
        JpsSdkTableSerializer.setSdkReference(module.getSdkReferencesTable(), sdkName, sdkType);
        if (sdkType instanceof JpsJavaSdkTypeWrapper) {
          dependenciesList.addSdkDependency(JpsJavaSdkType.INSTANCE);
        }
      }
      else if (INHERITED_JDK_TYPE.equals(type)) {
        final JpsSdkType<?> sdkType = projectSdkType != null? projectSdkType : JpsJavaSdkType.INSTANCE;
        dependenciesList.addSdkDependency(sdkType);
        if (sdkType instanceof JpsJavaSdkTypeWrapper) {
          dependenciesList.addSdkDependency(JpsJavaSdkType.INSTANCE);
        }
      }
      else if (LIBRARY_TYPE.equals(type)) {
        String name = getRequiredAttribute(orderEntry, NAME_ATTRIBUTE);
        String level = getRequiredAttribute(orderEntry, LEVEL_ATTRIBUTE);
        JpsElementReference<? extends JpsCompositeElement> ref = JpsLibraryTableSerializer.createLibraryTableReference(level);
        final JpsLibraryDependency dependency = dependenciesList.addLibraryDependency(elementFactory.createLibraryReference(name, ref));
        loadModuleDependencyProperties(dependency, orderEntry);
      }
      else if (MODULE_LIBRARY_TYPE.equals(type)) {
        final Element moduleLibraryElement = orderEntry.getChild(LIBRARY_TAG);
        if (moduleLibraryElement != null) {
          String name = moduleLibraryElement.getAttributeValue(NAME_ATTRIBUTE);
          if (name == null) {
            name = GENERATED_LIBRARY_NAME_PREFIX;
          }
          String uniqueName = nameGenerator.generateUniqueName(name);
          final JpsLibrary library = JpsLibraryTableSerializer.loadLibrary(moduleLibraryElement, uniqueName, pathMapper);
          module.addModuleLibrary(library);

          final JpsLibraryDependency dependency = dependenciesList.addLibraryDependency(library);
          loadModuleDependencyProperties(dependency, orderEntry);
        }
      }
      else if (MODULE_TYPE.equals(type)) {
        String name = getRequiredAttribute(orderEntry, MODULE_NAME_ATTRIBUTE);
        final JpsModuleDependency dependency = dependenciesList.addModuleDependency(elementFactory.createModuleReference(name));
        loadModuleDependencyProperties(dependency, orderEntry);
      }
    }
    if (!moduleSourceAdded) {
      dependenciesList.addModuleSourceDependency();
    }

    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      extension.loadRootModel(module, rootModelComponent);
    }
  }

  @NotNull
  private static String getRequiredAttribute(Element element, String attribute) {
    final String url = element.getAttributeValue(attribute);
    if (url == null) {
      throw new JpsSerializationFormatException("'" + attribute + "' attribute is missing in '" + element.getName() + "' tag");
    }
    return url;
  }

  @NotNull
  public static JpsModuleSourceRoot loadSourceRoot(Element sourceElement) {
    final String sourceUrl = getRequiredAttribute(sourceElement, URL_ATTRIBUTE);
    JpsModuleSourceRootPropertiesSerializer<?> serializer = getSourceRootPropertiesSerializer(sourceElement);
    return createSourceRoot(sourceUrl, serializer, sourceElement);
  }

  @NotNull
  private static <P extends JpsElement> JpsModuleSourceRoot createSourceRoot(@NotNull String url,
                                                                             @NotNull JpsModuleSourceRootPropertiesSerializer<P> serializer,
                                                                             @NotNull Element sourceElement) {
    return JpsElementFactory.getInstance().createModuleSourceRoot(url, serializer.getType(), serializer.loadProperties(sourceElement));
  }

  @NotNull
  private static JpsModuleSourceRootPropertiesSerializer<?> getSourceRootPropertiesSerializer(@NotNull Element sourceElement) {
    String typeAttribute = sourceElement.getAttributeValue(SOURCE_ROOT_TYPE_ATTRIBUTE);
    if (typeAttribute == null) {
      typeAttribute = Boolean.parseBoolean(sourceElement.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE))? JAVA_TEST_ROOT_TYPE_ID : JAVA_SOURCE_ROOT_TYPE_ID;
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsModuleSourceRootPropertiesSerializer<?> serializer : extension.getModuleSourceRootPropertiesSerializers()) {
        if (serializer.getTypeId().equals(typeAttribute)) {
          return serializer;
        }
      }
    }
    LOG.warn("Unknown module source root type " + typeAttribute);
    return UnknownSourceRootPropertiesSerializer.forType(UnknownSourceRootType.getInstance(typeAttribute));
  }

  public static <P extends JpsElement> void saveSourceRoot(@NotNull Element contentElement,
                                                           final @NotNull String rootUrl,
                                                           @NotNull JpsTypedModuleSourceRoot<P> root) {
    Element sourceElement = new Element(SOURCE_FOLDER_TAG);
    sourceElement.setAttribute(URL_ATTRIBUTE, rootUrl);
    JpsModuleSourceRootPropertiesSerializer<P> serializer = getSerializer(root.getRootType());
    if (serializer != null) {
      String typeId = serializer.getTypeId();
      if (!typeId.equals(JAVA_SOURCE_ROOT_TYPE_ID) && !typeId.equals(JAVA_TEST_ROOT_TYPE_ID)) {
        sourceElement.setAttribute(SOURCE_ROOT_TYPE_ATTRIBUTE, typeId);
      }
      serializer.saveProperties(root.getProperties(), sourceElement);
    }
    contentElement.addContent(sourceElement);
  }

  @Nullable
  private static <P extends JpsElement> JpsModuleSourceRootPropertiesSerializer<P> getSerializer(JpsModuleSourceRootType<P> type) {
    if (type instanceof UnknownSourceRootType) {
      return (JpsModuleSourceRootPropertiesSerializer<P>)UnknownSourceRootPropertiesSerializer.forType((UnknownSourceRootType)type);
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsModuleSourceRootPropertiesSerializer<?> serializer : extension.getModuleSourceRootPropertiesSerializers()) {
        if (serializer.getType().equals(type)) {
          return (JpsModuleSourceRootPropertiesSerializer<P>)serializer;
        }
      }
    }
    return null;
  }

  private static void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      extension.loadModuleDependencyProperties(dependency, orderEntry);
    }
  }
}
