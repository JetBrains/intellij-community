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
package org.jetbrains.jps.model.serialization.module;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.JpsJavaSdkTypeWrapper;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.JDOMUtil.getChildren;

/**
 * @author nik
 */
public class JpsModuleRootModelSerializer {
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
  private static final String SOURCE_ROOT_TYPE_ATTRIBUTE = "type";
  public static final String JAVA_SOURCE_ROOT_TYPE_ID = "java-source";
  public static final String JAVA_TEST_ROOT_TYPE_ID = "java-test";
  private static final String GENERATED_LIBRARY_NAME_PREFIX = "#";

  public static void loadRootModel(JpsModule module, @Nullable Element rootModelComponent, @Nullable JpsSdkType<?> projectSdkType) {
    if (rootModelComponent == null) return;

    for (Element contentElement : getChildren(rootModelComponent, CONTENT_TAG)) {
      final String url = contentElement.getAttributeValue(URL_ATTRIBUTE);
      module.getContentRootsList().addUrl(url);
      for (Element sourceElement : getChildren(contentElement, SOURCE_FOLDER_TAG)) {
        module.addSourceRoot(loadSourceRoot(sourceElement));
      }
      for (Element excludeElement : getChildren(contentElement, EXCLUDE_FOLDER_TAG)) {
        module.getExcludeRootsList().addUrl(excludeElement.getAttributeValue(URL_ATTRIBUTE));
      }
      for (Element excludePatternElement : getChildren(contentElement, EXCLUDE_PATTERN_TAG)) {
        module.addExcludePattern(url, excludePatternElement.getAttributeValue(EXCLUDE_PATTERN_ATTRIBUTE));
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
        String sdkName = orderEntry.getAttributeValue(JDK_NAME_ATTRIBUTE);
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
        String name = orderEntry.getAttributeValue(NAME_ATTRIBUTE);
        String level = orderEntry.getAttributeValue(LEVEL_ATTRIBUTE);
        if (name != null && level != null) {
          JpsElementReference<? extends JpsCompositeElement> ref = JpsLibraryTableSerializer.createLibraryTableReference(level);
          final JpsLibraryDependency dependency = dependenciesList.addLibraryDependency(elementFactory.createLibraryReference(name, ref));
          loadModuleDependencyProperties(dependency, orderEntry);
        }
        else {
          String missing = name == null ? NAME_ATTRIBUTE : LEVEL_ATTRIBUTE;
          LOG.warn("Incorrect '" + LIBRARY_TYPE + "' entry in '" + module.getName() + "' module: '" + missing + "' attribute isn't specified");
        }
      }
      else if (MODULE_LIBRARY_TYPE.equals(type)) {
        final Element moduleLibraryElement = orderEntry.getChild(LIBRARY_TAG);
        if (moduleLibraryElement != null) {
          String name = moduleLibraryElement.getAttributeValue(NAME_ATTRIBUTE);
          if (name == null) {
            name = GENERATED_LIBRARY_NAME_PREFIX;
          }
          String uniqueName = nameGenerator.generateUniqueName(name);
          final JpsLibrary library = JpsLibraryTableSerializer.loadLibrary(moduleLibraryElement, uniqueName);
          module.addModuleLibrary(library);

          final JpsLibraryDependency dependency = dependenciesList.addLibraryDependency(library);
          loadModuleDependencyProperties(dependency, orderEntry);
        }
      }
      else if (MODULE_TYPE.equals(type)) {
        String name = orderEntry.getAttributeValue(MODULE_NAME_ATTRIBUTE);
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
  public static JpsModuleSourceRoot loadSourceRoot(Element sourceElement) {
    final String sourceUrl = sourceElement.getAttributeValue(URL_ATTRIBUTE);
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
      typeAttribute = Boolean.parseBoolean(sourceElement.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE)) ? JAVA_TEST_ROOT_TYPE_ID : JAVA_SOURCE_ROOT_TYPE_ID;
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsModuleSourceRootPropertiesSerializer<?> serializer : extension.getModuleSourceRootPropertiesSerializers()) {
        if (serializer.getTypeId().equals(typeAttribute)) {
          return serializer;
        }
      }
    }
    LOG.warn("Unknown module source root type " + typeAttribute);
    return JpsJavaModelSerializerExtension.JAVA_SOURCE_ROOT_PROPERTIES_SERIALIZER;
  }

  public static void saveRootModel(JpsModule module, Element rootModelElement) {
    List<JpsModuleSourceRoot> sourceRoots = module.getSourceRoots();
    List<String> excludedUrls = getSortedList(module.getExcludeRootsList().getUrls());
    for (String url : getSortedList(module.getContentRootsList().getUrls())) {
      Element contentElement = new Element(CONTENT_TAG);
      contentElement.setAttribute(URL_ATTRIBUTE, url);
      rootModelElement.addContent(contentElement);
      for (JpsModuleSourceRoot root : sourceRoots) {
        if (FileUtil.startsWith(root.getUrl(), url)) {
          saveSourceRoot(contentElement, root.asTyped().getUrl(), root.asTyped());
        }
      }
      for (String excludedUrl : excludedUrls) {
        if (FileUtil.startsWith(excludedUrl, url)) {
          Element element = new Element(EXCLUDE_FOLDER_TAG).setAttribute(URL_ATTRIBUTE, excludedUrl);
          contentElement.addContent(element);
        }
      }
      for (JpsExcludePattern pattern : module.getExcludePatterns()) {
        if (pattern.getBaseDirUrl().equals(url)) {
          contentElement.addContent(new Element(EXCLUDE_PATTERN_TAG).setAttribute(EXCLUDE_PATTERN_ATTRIBUTE, pattern.getPattern()));
        }
      }
    }

    for (JpsDependencyElement dependency : module.getDependenciesList().getDependencies()) {
      if (dependency instanceof JpsModuleSourceDependency) {
        rootModelElement.addContent(createDependencyElement(SOURCE_FOLDER_TYPE).setAttribute("forTests", "false"));
      }
      else if (dependency instanceof JpsSdkDependency) {
        JpsSdkType<?> sdkType = ((JpsSdkDependency)dependency).getSdkType();
        JpsSdkReferencesTable table = module.getSdkReferencesTable();
        JpsSdkReference<?> reference = table.getSdkReference(sdkType);
        if (reference == null) {
          rootModelElement.addContent(createDependencyElement(INHERITED_JDK_TYPE));
        }
        else {
          Element element = createDependencyElement(JDK_TYPE);
          element.setAttribute(JDK_NAME_ATTRIBUTE, reference.getSdkName());
          element.setAttribute(JDK_TYPE_ATTRIBUTE, JpsSdkTableSerializer.getLoader(sdkType).getTypeId());
          rootModelElement.addContent(element);
        }
      }
      else if (dependency instanceof JpsLibraryDependency) {
        JpsLibraryReference reference = ((JpsLibraryDependency)dependency).getLibraryReference();
        JpsElementReference<? extends JpsCompositeElement> parentReference = reference.getParentReference();
        Element element;
        if (parentReference instanceof JpsModuleReference) {
          element = createDependencyElement(MODULE_LIBRARY_TYPE);
          saveModuleDependencyProperties(dependency, element);
          Element libraryElement = new Element(LIBRARY_TAG);
          JpsLibrary library = reference.resolve();
          String libraryName = library.getName();
          JpsLibraryTableSerializer.saveLibrary(library, libraryElement, isGeneratedName(libraryName) ? null : libraryName);
          element.addContent(libraryElement);
        }
        else {
          element = createDependencyElement(LIBRARY_TYPE);
          saveModuleDependencyProperties(dependency, element);
          element.setAttribute(NAME_ATTRIBUTE, reference.getLibraryName());
          element.setAttribute(LEVEL_ATTRIBUTE, JpsLibraryTableSerializer.getLevelId(parentReference));
        }
        rootModelElement.addContent(element);
      }
      else if (dependency instanceof JpsModuleDependency) {
        Element element = createDependencyElement(MODULE_TYPE);
        element.setAttribute(MODULE_NAME_ATTRIBUTE, ((JpsModuleDependency)dependency).getModuleReference().getModuleName());
        saveModuleDependencyProperties(dependency, element);
        rootModelElement.addContent(element);
      }
    }

    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      extension.saveRootModel(module, rootModelElement);
    }
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
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsModuleSourceRootPropertiesSerializer<?> serializer : extension.getModuleSourceRootPropertiesSerializers()) {
        if (serializer.getType().equals(type)) {
          return (JpsModuleSourceRootPropertiesSerializer<P>)serializer;
        }
      }
    }
    return null;
  }

  private static boolean isGeneratedName(String libraryName) {
    return libraryName.startsWith(GENERATED_LIBRARY_NAME_PREFIX);
  }

  private static Element createDependencyElement(final String type) {
    return new Element(ORDER_ENTRY_TAG).setAttribute(TYPE_ATTRIBUTE, type);
  }

  private static List<String> getSortedList(final List<String> list) {
    List<String> strings = new ArrayList<>(list);
    Collections.sort(strings);
    return strings;
  }

  private static void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      extension.loadModuleDependencyProperties(dependency, orderEntry);
    }
  }

  private static void saveModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      extension.saveModuleDependencyProperties(dependency, orderEntry);
    }
  }
}
