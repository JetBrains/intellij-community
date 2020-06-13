// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.library;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class JpsLibraryTableSerializer {
  private static final JpsLibraryRootTypeSerializer[] PREDEFINED_ROOT_TYPES_SERIALIZERS = {
    new JpsLibraryRootTypeSerializer("CLASSES", JpsOrderRootType.COMPILED, true),
    new JpsLibraryRootTypeSerializer("SOURCES", JpsOrderRootType.SOURCES, true)
  };
  public static final String NAME_ATTRIBUTE = "name";
  public static final String TYPE_ATTRIBUTE = "type";
  public static final String PROPERTIES_TAG = "properties";
  public static final String JAR_DIRECTORY_TAG = "jarDirectory";
  private static final String URL_ATTRIBUTE = "url";
  public static final String ROOT_TAG = "root";
  public static final String RECURSIVE_ATTRIBUTE = "recursive";
  public static final String LIBRARY_TAG = "library";
  private static final JpsLibraryPropertiesSerializer<JpsDummyElement> JAVA_LIBRARY_PROPERTIES_SERIALIZER =
    new JpsLibraryPropertiesSerializer<JpsDummyElement>(JpsJavaLibraryType.INSTANCE, null) {
      @Override
      public JpsDummyElement loadProperties(@Nullable Element propertiesElement) {
        return JpsElementFactory.getInstance().createDummyElement();
      }

      @Override
      public void saveProperties(JpsDummyElement properties, Element element) {
      }
    };
  public static final String MODULE_LEVEL = "module";
  public static final String PROJECT_LEVEL = "project";
  public static final String APPLICATION_LEVEL = "application";

  public static void loadLibraries(@Nullable Element libraryTableElement, JpsLibraryCollection result) {
    for (Element libraryElement : JDOMUtil.getChildren(libraryTableElement, LIBRARY_TAG)) {
      result.addLibrary(loadLibrary(libraryElement));
    }
  }

  public static void saveLibraries(JpsLibraryCollection libraryCollection, Element libraryTableElement) {
    List<JpsLibrary> libraries = new ArrayList<>();
    for (JpsLibrary library : libraryCollection.getLibraries()) {
      if (!(library.getType() instanceof JpsSdkType<?>)) {
        libraries.add(library);
      }
    }

    libraries.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

    for (JpsLibrary library : libraries) {
      Element libraryTag = new Element(LIBRARY_TAG);
      saveLibrary(library, libraryTag, library.getName());
      libraryTableElement.addContent(libraryTag);
    }
  }

  public static JpsLibrary loadLibrary(Element libraryElement) {
    return loadLibrary(libraryElement, libraryElement.getAttributeValue(NAME_ATTRIBUTE));
  }

  public static JpsLibrary loadLibrary(Element libraryElement, String name) {
    String typeId = libraryElement.getAttributeValue(TYPE_ATTRIBUTE);
    final JpsLibraryPropertiesSerializer<?> loader = getLibraryPropertiesSerializer(typeId);
    JpsLibrary library = createLibrary(name, loader, libraryElement.getChild(PROPERTIES_TAG));

    MultiMap<JpsOrderRootType, String> jarDirectories = new MultiMap<>();
    MultiMap<JpsOrderRootType, String> recursiveJarDirectories = new MultiMap<>();
    for (Element jarDirectory : JDOMUtil.getChildren(libraryElement, JAR_DIRECTORY_TAG)) {
      String url = jarDirectory.getAttributeValue(URL_ATTRIBUTE);
      String rootTypeId = jarDirectory.getAttributeValue(TYPE_ATTRIBUTE);
      final JpsOrderRootType rootType = rootTypeId != null ? getRootType(rootTypeId) : JpsOrderRootType.COMPILED;
      boolean recursive = Boolean.parseBoolean(jarDirectory.getAttributeValue(RECURSIVE_ATTRIBUTE));
      jarDirectories.putValue(rootType, url);
      if (recursive) {
        recursiveJarDirectories.putValue(rootType, url);
      }
    }
    for (Element rootsElement : libraryElement.getChildren()) {
      final String rootTypeId = rootsElement.getName();
      if (!rootTypeId.equals(JAR_DIRECTORY_TAG) && !rootTypeId.equals(PROPERTIES_TAG)) {
        final JpsOrderRootType rootType = getRootType(rootTypeId);
        for (Element rootElement : JDOMUtil.getChildren(rootsElement, ROOT_TAG)) {
          String url = rootElement.getAttributeValue(URL_ATTRIBUTE);
          JpsLibraryRoot.InclusionOptions options;
          if (jarDirectories.get(rootType).contains(url)) {
            final boolean recursive = recursiveJarDirectories.get(rootType).contains(url);
            options = recursive ? JpsLibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY : JpsLibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT;
          }
          else {
            options = JpsLibraryRoot.InclusionOptions.ROOT_ITSELF;
          }
          library.addRoot(url, rootType, options);
        }
      }
    }
    return library;
  }

  public static void saveLibrary(JpsLibrary library, Element libraryElement, final String libraryName) {
    if (libraryName != null) {
      libraryElement.setAttribute(NAME_ATTRIBUTE, libraryName);
    }
    saveProperties((JpsTypedLibrary<?>)library, libraryElement);
    List<Element> jarDirectoryElements = new ArrayList<>();
    for (JpsLibraryRootTypeSerializer serializer : getSortedSerializers()) {
      List<JpsLibraryRoot> roots = library.getRoots(serializer.getType());
      if (roots.isEmpty() && !serializer.isWriteIfEmpty()) continue;

      Element typeElement = new Element(serializer.getTypeId());
      for (JpsLibraryRoot root : roots) {
        typeElement.addContent(new Element(ROOT_TAG).setAttribute(URL_ATTRIBUTE, root.getUrl()));
        if (root.getInclusionOptions() != JpsLibraryRoot.InclusionOptions.ROOT_ITSELF) {
          Element jarDirectoryElement = new Element(JAR_DIRECTORY_TAG).setAttribute(URL_ATTRIBUTE, root.getUrl());
          boolean recursive = root.getInclusionOptions() == JpsLibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY;
          jarDirectoryElement.setAttribute(RECURSIVE_ATTRIBUTE, Boolean.toString(recursive));
          if (!serializer.getType().equals(JpsOrderRootType.COMPILED)) {
            jarDirectoryElement.setAttribute(TYPE_ATTRIBUTE, serializer.getTypeId());
          }
          jarDirectoryElements.add(jarDirectoryElement);
        }
      }
      libraryElement.addContent(typeElement);
    }
    libraryElement.addContent(jarDirectoryElements);
  }

  private static <P extends JpsElement> void saveProperties(JpsTypedLibrary<P> library, Element libraryElement) {
    JpsLibraryType<P> type = library.getType();
    if (!type.equals(JpsJavaLibraryType.INSTANCE)) {
      JpsLibraryPropertiesSerializer<P> serializer = getLibraryPropertiesSerializer(type);
      libraryElement.setAttribute(TYPE_ATTRIBUTE, serializer.getTypeId());
      Element element = new Element(PROPERTIES_TAG);
      serializer.saveProperties(library.getProperties(), element);
      if (!element.getContent().isEmpty() || !element.getAttributes().isEmpty()) {
        libraryElement.addContent(element);
      }
    }
  }

  private static <P extends JpsElement> JpsLibrary createLibrary(String name, JpsLibraryPropertiesSerializer<P> loader,
                                                                           final Element propertiesElement) {
    return JpsElementFactory.getInstance().createLibrary(name, loader.getType(), loader.loadProperties(propertiesElement));
  }

  private static JpsOrderRootType getRootType(String rootTypeId) {
    for (JpsLibraryRootTypeSerializer serializer : PREDEFINED_ROOT_TYPES_SERIALIZERS) {
      if (serializer.getTypeId().equals(rootTypeId)) {
        return serializer.getType();
      }
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsLibraryRootTypeSerializer serializer : extension.getLibraryRootTypeSerializers()) {
        if (serializer.getTypeId().equals(rootTypeId)) {
          return serializer.getType();
        }
      }
    }
    return JpsOrderRootType.COMPILED;
  }

  private static Collection<JpsLibraryRootTypeSerializer> getSortedSerializers() {
    List<JpsLibraryRootTypeSerializer> serializers = new ArrayList<>();
    Collections.addAll(serializers, PREDEFINED_ROOT_TYPES_SERIALIZERS);
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      serializers.addAll(extension.getLibraryRootTypeSerializers());
    }
    Collections.sort(serializers);
    return serializers;
  }

  private static JpsLibraryPropertiesSerializer<?> getLibraryPropertiesSerializer(@Nullable String typeId) {
    if (typeId != null) {
      for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
        for (JpsLibraryPropertiesSerializer<?> loader : extension.getLibraryPropertiesSerializers()) {
          if (loader.getTypeId().equals(typeId)) {
            return loader;
          }
        }
      }
    }
    return JAVA_LIBRARY_PROPERTIES_SERIALIZER;
  }

  private static <P extends JpsElement> JpsLibraryPropertiesSerializer<P> getLibraryPropertiesSerializer(@NotNull JpsLibraryType<P> type) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsLibraryPropertiesSerializer<?> loader : extension.getLibraryPropertiesSerializers()) {
        if (loader.getType().equals(type)) {
          //noinspection unchecked
          return (JpsLibraryPropertiesSerializer<P>)loader;
        }
      }
    }
    throw new IllegalArgumentException("unknown type library:" + type);
  }

  public static JpsElementReference<? extends JpsCompositeElement> createLibraryTableReference(String level) {
    JpsElementFactory elementFactory = JpsElementFactory.getInstance();
    if (level.equals(PROJECT_LEVEL)) {
      return elementFactory.createProjectReference();
    }
    if (level.equals(APPLICATION_LEVEL)) {
      return elementFactory.createGlobalReference();
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      final JpsElementReference<? extends JpsCompositeElement> reference = extension.createLibraryTableReference(level);
      if (reference != null) {
        return reference;
      }
    }
    throw new UnsupportedOperationException();
  }

  public static String getLevelId(JpsElementReference<? extends JpsCompositeElement> reference) {
    if (reference instanceof JpsModuleReference) {
      return MODULE_LEVEL;
    }
    JpsCompositeElement element = reference.resolve();
    if (element instanceof JpsProject) {
      return PROJECT_LEVEL;
    }
    else if (element instanceof JpsGlobal) {
      return APPLICATION_LEVEL;
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      String levelId = extension.getLibraryTableLevelId(reference);
      if (levelId != null) {
        return levelId;
      }
    }
    return null;
  }
}
