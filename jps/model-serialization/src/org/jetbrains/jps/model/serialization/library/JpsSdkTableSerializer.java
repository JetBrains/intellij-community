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
package org.jetbrains.jps.model.serialization.library;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.JpsJavaSdkTypeWrapper;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsSdkTableSerializer {
  private static final Logger LOG = Logger.getInstance(JpsSdkTableSerializer.class);

  private static final JpsLibraryRootTypeSerializer[] PREDEFINED_ROOT_TYPE_SERIALIZERS = {
    new JpsLibraryRootTypeSerializer("classPath", JpsOrderRootType.COMPILED, true),
    new JpsLibraryRootTypeSerializer("sourcePath", JpsOrderRootType.SOURCES, true)
  };
  private static final JpsSdkPropertiesSerializer<JpsDummyElement> JPS_JAVA_SDK_PROPERTIES_LOADER =
    new JpsSdkPropertiesSerializer<JpsDummyElement>("JavaSDK", JpsJavaSdkType.INSTANCE) {
      @NotNull
      @Override
      public JpsDummyElement loadProperties(Element propertiesElement) {
        return JpsElementFactory.getInstance().createDummyElement();
      }

      @Override
      public void saveProperties(@NotNull JpsDummyElement properties, @NotNull Element element) {
      }
    };
  private static final String JDK_TAG = "jdk";
  private static final String NAME_TAG = "name";
  private static final String TYPE_TAG = "type";
  private static final String TYPE_ATTRIBUTE = "type";
  private static final String ROOTS_TAG = "roots";
  private static final String ROOT_TAG = "root";
  private static final String VERSION_TAG = "version";
  private static final String HOME_PATH_TAG = "homePath";
  private static final String VALUE_ATTRIBUTE = "value";
  private static final String COMPOSITE_TYPE = "composite";
  private static final String SIMPLE_TYPE = "simple";
  private static final String URL_ATTRIBUTE = "url";
  private static final String ADDITIONAL_TAG = "additional";

  public static void loadSdks(@Nullable Element sdkListElement, JpsLibraryCollection result) {
    for (Element sdkElement : JDOMUtil.getChildren(sdkListElement, JDK_TAG)) {
      result.addLibrary(loadSdk(sdkElement));
    }
  }

  public static void saveSdks(JpsLibraryCollection libraryCollection, Element sdkListElement) {
    for (JpsLibrary library : libraryCollection.getLibraries()) {
      JpsElement properties = library.getProperties();
      if (properties instanceof JpsSdk<?>) {
        Element sdkTag = new Element(JDK_TAG);
        saveSdk((JpsSdk<?>)properties, sdkTag);
        sdkListElement.addContent(sdkTag);
      }
    }
  }

  private static JpsLibrary loadSdk(Element sdkElement) {
    String name = getAttributeValue(sdkElement, NAME_TAG);
    String typeId = getAttributeValue(sdkElement, TYPE_TAG);
    LOG.debug("Loading " + typeId + " SDK '" + name + "'");
    JpsSdkPropertiesSerializer<?> serializer = getSdkPropertiesSerializer(typeId);
    final JpsLibrary library = createSdk(name, serializer, sdkElement);
    final Element roots = sdkElement.getChild(ROOTS_TAG);
    for (Element rootTypeElement : JDOMUtil.getChildren(roots)) {
      JpsLibraryRootTypeSerializer rootTypeSerializer = getRootTypeSerializer(rootTypeElement.getName());
      if (rootTypeSerializer != null) {
        for (Element rootElement : JDOMUtil.getChildren(rootTypeElement)) {
          loadRoots(rootElement, library, rootTypeSerializer.getType());
        }
      }
      else {
        LOG.info("root type serializer not found for " + rootTypeElement.getName());
      }
    }
    if (LOG.isDebugEnabled()) {
      List<File> files = library.getFiles(JpsOrderRootType.COMPILED);
      LOG.debug(name + " SDK classpath (" + files.size() + " roots):");
      for (File file : files) {
        LOG.debug(" " + file.getAbsolutePath());
      }
    }
    return library;
  }

  private static <P extends JpsElement> void saveSdk(final JpsSdk<P> sdk, Element sdkTag) {
    JpsLibrary library = sdk.getParent();
    sdkTag.setAttribute("version", "2");
    setAttributeValue(sdkTag, NAME_TAG, library.getName());
    JpsSdkPropertiesSerializer<P> serializer = getSdkPropertiesSerializer(sdk.getSdkType());
    setAttributeValue(sdkTag, TYPE_TAG, serializer.getTypeId());
    String versionString = sdk.getVersionString();
    if (versionString != null) {
      setAttributeValue(sdkTag, VERSION_TAG, versionString);
    }
    setAttributeValue(sdkTag, HOME_PATH_TAG, sdk.getHomePath());

    Element rootsTag = new Element(ROOTS_TAG);
    for (JpsLibraryRootTypeSerializer rootTypeSerializer : getRootTypeSerializers()) {
      Element rootTypeTag = new Element(rootTypeSerializer.getTypeId());
      Element compositeTag = new Element(ROOT_TAG);
      compositeTag.setAttribute(TYPE_ATTRIBUTE, COMPOSITE_TYPE);
      List<JpsLibraryRoot> roots = library.getRoots(rootTypeSerializer.getType());
      for (JpsLibraryRoot root : roots) {
        compositeTag.addContent(new Element(ROOT_TAG).setAttribute(TYPE_ATTRIBUTE, SIMPLE_TYPE).setAttribute(URL_ATTRIBUTE, root.getUrl()));
      }
      rootTypeTag.addContent(compositeTag);
      rootsTag.addContent(rootTypeTag);
    }
    sdkTag.addContent(rootsTag);

    Element additionalTag = new Element(ADDITIONAL_TAG);
    serializer.saveProperties(sdk.getSdkProperties(), additionalTag);
    sdkTag.addContent(additionalTag);
  }

  private static void setAttributeValue(Element tag, final String tagName, final String value) {
    tag.addContent(new Element(tagName).setAttribute(VALUE_ATTRIBUTE, value));
  }

  private static void loadRoots(Element rootElement, JpsLibrary library, JpsOrderRootType rootType) {
    final String type = rootElement.getAttributeValue(TYPE_ATTRIBUTE);
    if (type.equals(COMPOSITE_TYPE)) {
      for (Element element : JDOMUtil.getChildren(rootElement)) {
        loadRoots(element, library, rootType);
      }
    }
    else if (type.equals(SIMPLE_TYPE)) {
      library.addRoot(rootElement.getAttributeValue(URL_ATTRIBUTE), rootType);
    }
  }

  @Nullable
  private static JpsLibraryRootTypeSerializer getRootTypeSerializer(String typeId) {
    for (JpsLibraryRootTypeSerializer serializer : PREDEFINED_ROOT_TYPE_SERIALIZERS) {
      if (serializer.getTypeId().equals(typeId)) {
        return serializer;
      }
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsLibraryRootTypeSerializer serializer : extension.getSdkRootTypeSerializers()) {
        if (serializer.getTypeId().equals(typeId)) {
          return serializer;
        }
      }
    }
    return null;
  }

  private static List<JpsLibraryRootTypeSerializer> getRootTypeSerializers() {
    List<JpsLibraryRootTypeSerializer> serializers = new ArrayList<JpsLibraryRootTypeSerializer>(Arrays.asList(PREDEFINED_ROOT_TYPE_SERIALIZERS));
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      serializers.addAll(extension.getSdkRootTypeSerializers());
    }
    Collections.sort(serializers);
    return serializers;
  }

  private static <P extends JpsElement> JpsLibrary createSdk(String name, JpsSdkPropertiesSerializer<P> loader, Element sdkElement) {
    String versionString = getAttributeValue(sdkElement, VERSION_TAG);
    String homePath = getAttributeValue(sdkElement, HOME_PATH_TAG);
    Element propertiesTag = sdkElement.getChild(ADDITIONAL_TAG);
    P properties = loader.loadProperties(propertiesTag);
    return JpsElementFactory.getInstance().createSdk(name, homePath, versionString, loader.getType(), properties);
  }

  public static JpsSdkPropertiesSerializer<?> getSdkPropertiesSerializer(@Nullable String typeId) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsSdkPropertiesSerializer<?> loader : extension.getSdkPropertiesSerializers()) {
        if (loader.getTypeId().equals(typeId)) {
          return loader;
        }
      }
    }
    return JPS_JAVA_SDK_PROPERTIES_LOADER;
  }

  public static <P extends JpsElement> JpsSdkPropertiesSerializer<P> getSdkPropertiesSerializer(JpsSdkType<P> type) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsSdkPropertiesSerializer<?> loader : extension.getSdkPropertiesSerializers()) {
        if (loader.getType().equals(type)) {
          //noinspection unchecked
          return (JpsSdkPropertiesSerializer<P>)loader;
        }
      }
    }
    //noinspection unchecked
    return (JpsSdkPropertiesSerializer<P>)JPS_JAVA_SDK_PROPERTIES_LOADER;
  }

  @Nullable
  private static String getAttributeValue(Element element, String childName) {
    final Element child = element.getChild(childName);
    return child != null ? child.getAttributeValue(VALUE_ATTRIBUTE) : null;
  }

  public static JpsSdkType<?> getSdkType(@Nullable String typeId) {
    return getSdkPropertiesSerializer(typeId).getType();
  }

  public static JpsSdkPropertiesSerializer<?> getLoader(JpsSdkType<?> type) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsSdkPropertiesSerializer<?> loader : extension.getSdkPropertiesSerializers()) {
        if (loader.getType().equals(type)) {
          return loader;
        }
      }
    }
    return JPS_JAVA_SDK_PROPERTIES_LOADER;
  }

  public static <P extends JpsElement> void setSdkReference(final JpsSdkReferencesTable table, String sdkName, JpsSdkType<P> sdkType) {
    JpsSdkReference<P> reference = JpsElementFactory.getInstance().createSdkReference(sdkName, sdkType);
    table.setSdkReference(sdkType, reference);
    if (sdkType instanceof JpsJavaSdkTypeWrapper) {
      JpsSdkReference<P> wrapperRef = JpsElementFactory.getInstance().createSdkReference(sdkName, sdkType);
      table.setSdkReference(JpsJavaSdkType.INSTANCE, JpsJavaExtensionService.getInstance().createWrappedJavaSdkReference((JpsJavaSdkTypeWrapper)sdkType,
                                                                                                                         wrapperRef));
    }
  }
}
