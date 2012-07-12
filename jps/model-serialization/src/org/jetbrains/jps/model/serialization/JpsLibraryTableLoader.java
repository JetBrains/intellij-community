package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.DummyJpsElementProperties;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsLibraryTableLoader {
  private static final Map<String, JpsOrderRootType> PREDEFINED_ROOT_TYPES = new HashMap<String, JpsOrderRootType>();

  static {
    PREDEFINED_ROOT_TYPES.put("CLASSES", JpsOrderRootType.COMPILED);
    PREDEFINED_ROOT_TYPES.put("SOURCES", JpsOrderRootType.SOURCES);
  }

  public static void loadLibraries(Element libraryTableElement, JpsLibraryCollection result) {
    for (Element libraryElement : JDOMUtil.getChildren(libraryTableElement, "library")) {
      result.addLibrary(loadLibrary(libraryElement));
    }
  }

  public static JpsLibrary loadLibrary(Element libraryElement) {
    return loadLibrary(libraryElement, libraryElement.getAttributeValue("name"));
  }

  public static JpsLibrary loadLibrary(Element libraryElement, String name) {
    String typeId = libraryElement.getAttributeValue("type");
    final JpsLibraryPropertiesLoader<?> loader = getLibraryPropertiesLoader(typeId);
    JpsLibrary library = createLibrary(name, loader, libraryElement.getChild("properties"));

    MultiMap<JpsOrderRootType, String> jarDirectories = new MultiMap<JpsOrderRootType, String>();
    MultiMap<JpsOrderRootType, String> recursiveJarDirectories = new MultiMap<JpsOrderRootType, String>();
    for (Element jarDirectory : JDOMUtil.getChildren(libraryElement, "jarDirectory")) {
      String url = jarDirectory.getAttributeValue("url");
      String rootTypeId = jarDirectory.getAttributeValue("type");
      final JpsOrderRootType rootType = rootTypeId != null ? getRootType(rootTypeId) : JpsOrderRootType.COMPILED;
      boolean recursive = Boolean.parseBoolean(jarDirectory.getAttributeValue("recursive"));
      jarDirectories.putValue(rootType, url);
      if (recursive) {
        recursiveJarDirectories.putValue(rootType, url);
      }
    }
    for (Element rootsElement : JDOMUtil.getChildren(libraryElement)) {
      final String rootTypeId = rootsElement.getName();
      if (!rootTypeId.equals("jarDirectory")) {
        final JpsOrderRootType rootType = getRootType(rootTypeId);
        for (Element rootElement : JDOMUtil.getChildren(rootsElement, "root")) {
          String url = rootElement.getAttributeValue("url");
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

  private static <P extends JpsElementProperties> JpsLibrary createLibrary(String name, JpsLibraryPropertiesLoader<P> loader,
                                                                           final Element propertiesElement) {
    return JpsElementFactory.getInstance().createLibrary(name, loader.getType(), loader.loadProperties(propertiesElement));
  }

  private static JpsOrderRootType getRootType(String rootTypeId) {
    final JpsOrderRootType type = PREDEFINED_ROOT_TYPES.get(rootTypeId);
    if (type != null) {
      return type;
    }
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      final JpsOrderRootType rootType = extension.getRootType(rootTypeId);
      if (rootType != null) {
        return rootType;
      }
    }
    return JpsOrderRootType.COMPILED;
  }

  private static JpsLibraryPropertiesLoader<?> getLibraryPropertiesLoader(@Nullable String typeId) {
    if (typeId != null) {
      for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
        for (JpsLibraryPropertiesLoader<?> loader : extension.getLibraryPropertiesLoaders()) {
          if (loader.getTypeId().equals(typeId)) {
            return loader;
          }
        }
      }
    }
    return new JpsLibraryPropertiesLoader<DummyJpsElementProperties>(JpsJavaLibraryType.INSTANCE, null) {
      @Override
      public DummyJpsElementProperties loadProperties(@Nullable Element propertiesElement) {
        return DummyJpsElementProperties.INSTANCE;
      }
    };
  }
}
