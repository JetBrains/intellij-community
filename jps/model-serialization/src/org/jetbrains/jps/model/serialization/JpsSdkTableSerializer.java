package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.JpsJavaSdkTypeWrapper;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsSdkTableSerializer {
  private static final JpsLibraryRootTypeSerializer[] PREDEFINED_ROOT_TYPE_SERIALIZERS = {
    new JpsLibraryRootTypeSerializer("classPath", JpsOrderRootType.COMPILED, true),
    new JpsLibraryRootTypeSerializer("sourcePath", JpsOrderRootType.SOURCES, true)
  };
  private static final JpsSdkPropertiesSerializer<JpsSdkProperties> JPS_JAVA_SDK_PROPERTIES_LOADER =
    new JpsSdkPropertiesSerializer<JpsSdkProperties>("JavaSDK", JpsJavaSdkType.INSTANCE) {
      @NotNull
      @Override
      public JpsSdkProperties loadProperties(String homePath, String version, Element propertiesElement) {
        return new JpsSdkProperties(homePath, version);
      }

      @Override
      public void saveProperties(@NotNull JpsSdkProperties properties, @NotNull Element element) {
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
      JpsLibraryType<?> type = library.getType();
      if (type instanceof JpsSdkType<?>) {
        Element sdkTag = new Element(JDK_TAG);
        //noinspection unchecked
        saveSdk((JpsTypedLibrary)library, (JpsSdkType)type, sdkTag);
        sdkListElement.addContent(sdkTag);
      }
    }
  }

  private static JpsLibrary loadSdk(Element sdkElement) {
    String name = getAttributeValue(sdkElement, NAME_TAG);
    String typeId = getAttributeValue(sdkElement, TYPE_TAG);
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
    }
    return library;
  }

  private static <P extends JpsSdkProperties> void saveSdk(JpsTypedLibrary<P> library, JpsSdkType<P> type, Element sdkTag) {
    sdkTag.setAttribute("version", "2");
    setAttributeValue(sdkTag, NAME_TAG, library.getName());
    JpsSdkPropertiesSerializer<P> serializer = getSdkPropertiesSerializer(type);
    setAttributeValue(sdkTag, TYPE_TAG, serializer.getTypeId());
    P properties = library.getProperties();
    String versionString = properties.getVersionString();
    if (versionString != null) {
      setAttributeValue(sdkTag, VERSION_TAG, versionString);
    }
    setAttributeValue(sdkTag, HOME_PATH_TAG, properties.getHomePath());

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
    serializer.saveProperties(properties, additionalTag);
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

  private static <P extends JpsSdkProperties> JpsLibrary createSdk(String name, JpsSdkPropertiesSerializer<P> loader, Element sdkElement) {
    String versionString = getAttributeValue(sdkElement, VERSION_TAG);
    String homePath = getAttributeValue(sdkElement, HOME_PATH_TAG);
    Element propertiesTag = sdkElement.getChild(ADDITIONAL_TAG);
    P properties = loader.loadProperties(homePath, versionString, propertiesTag);
    return JpsElementFactory.getInstance().createLibrary(name, loader.getType(), properties);
  }

  public static JpsSdkPropertiesSerializer<?> getSdkPropertiesSerializer(String typeId) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsSdkPropertiesSerializer<?> loader : extension.getSdkPropertiesSerializers()) {
        if (loader.getTypeId().equals(typeId)) {
          return loader;
        }
      }
    }
    return JPS_JAVA_SDK_PROPERTIES_LOADER;
  }

  public static <P extends JpsSdkProperties> JpsSdkPropertiesSerializer<P> getSdkPropertiesSerializer(JpsSdkType<P> type) {
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

  public static JpsSdkType<?> getSdkType(String typeId) {
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

  public static void setSdkReference(final JpsSdkReferencesTable table, String sdkName, JpsSdkType<?> sdkType) {
    JpsLibraryReference reference = JpsElementFactory.getInstance().createSdkReference(sdkName, sdkType);
    table.setSdkReference(sdkType, reference);
    if (sdkType instanceof JpsJavaSdkTypeWrapper) {
      JpsLibrary jpsLibrary = reference.resolve();
      if (jpsLibrary != null) {
        String name = ((JpsJavaSdkTypeWrapper)sdkType).getJavaSdkName((JpsSdkProperties)jpsLibrary.getProperties());
        if (name != null) {
          table.setSdkReference(JpsJavaSdkType.INSTANCE, JpsElementFactory.getInstance().createSdkReference(name, JpsJavaSdkType.INSTANCE));
        }
      }
    }
  }
}
