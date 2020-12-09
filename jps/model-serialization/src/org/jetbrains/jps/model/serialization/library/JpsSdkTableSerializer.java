// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsPathMapper;

import java.io.File;
import java.util.List;

public final class JpsSdkTableSerializer {
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
    };
  private static final String JDK_TAG = "jdk";
  private static final String NAME_TAG = "name";
  private static final String TYPE_TAG = "type";
  private static final String TYPE_ATTRIBUTE = "type";
  private static final String ROOTS_TAG = "roots";
  private static final String VERSION_TAG = "version";
  private static final String HOME_PATH_TAG = "homePath";
  private static final String VALUE_ATTRIBUTE = "value";
  private static final String COMPOSITE_TYPE = "composite";
  private static final String SIMPLE_TYPE = "simple";
  private static final String URL_ATTRIBUTE = "url";
  private static final String ADDITIONAL_TAG = "additional";

  public static void loadSdks(@Nullable Element sdkListElement,
                              JpsLibraryCollection result,
                              @NotNull JpsPathMapper pathMapper) {
    for (Element sdkElement : JDOMUtil.getChildren(sdkListElement, JDK_TAG)) {
      result.addLibrary(loadSdk(sdkElement, pathMapper));
    }
  }

  private static JpsLibrary loadSdk(Element sdkElement, @NotNull JpsPathMapper pathMapper) {
    String name = getAttributeValue(sdkElement, NAME_TAG);
    String typeId = getAttributeValue(sdkElement, TYPE_TAG);
    LOG.debug("Loading " + typeId + " SDK '" + name + "'");
    JpsSdkPropertiesSerializer<?> serializer = getSdkPropertiesSerializer(typeId);
    final JpsLibrary library = createSdk(name, serializer, sdkElement);
    final Element roots = sdkElement.getChild(ROOTS_TAG);
    for (Element rootTypeElement : JDOMUtil.getChildren(roots)) {
      JpsLibraryRootTypeSerializer rootTypeSerializer = getRootTypeSerializer(rootTypeElement.getName());
      if (rootTypeSerializer != null) {
        for (Element rootElement : rootTypeElement.getChildren()) {
          loadRoots(rootElement, library, rootTypeSerializer.getType(), pathMapper);
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

  private static void loadRoots(Element rootElement, JpsLibrary library, JpsOrderRootType rootType, @NotNull JpsPathMapper pathMapper) {
    final String type = rootElement.getAttributeValue(TYPE_ATTRIBUTE);
    if (type.equals(COMPOSITE_TYPE)) {
      for (Element element : rootElement.getChildren()) {
        loadRoots(element, library, rootType, pathMapper);
      }
    }
    else if (type.equals(SIMPLE_TYPE)) {
      String url = rootElement.getAttributeValue(URL_ATTRIBUTE);
      if (url == null) return;
      library.addRoot(pathMapper.mapUrl(url), rootType);
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
