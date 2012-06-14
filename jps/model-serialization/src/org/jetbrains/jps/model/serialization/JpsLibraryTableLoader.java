package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRootType;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.HashMap;
import java.util.List;
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

  public static void loadLibraries(Element libraryTableElement, List<JpsLibrary> result) {
    for (Element libraryElement : JDOMUtil.getChildren(libraryTableElement, "library")) {
      JpsLibrary library = loadLibrary(libraryElement);
      result.add(library);
    }
  }

  public static JpsLibrary loadLibrary(Element libraryElement) {
    String name = libraryElement.getAttributeValue("name");
    String typeId = libraryElement.getAttributeValue("type");
    JpsLibrary library = JpsElementFactory.getInstance().createLibrary(name, getLibraryType(typeId));

    MultiMap<JpsOrderRootType, String> jarDirectories = new MultiMap<JpsOrderRootType, String>();
    MultiMap<JpsOrderRootType, String> recursiveJarDirectories = new MultiMap<JpsOrderRootType, String>();
    for (Element jarDirectory : JDOMUtil.getChildren(libraryElement, "jarDirectory")) {
      String url = jarDirectory.getAttributeValue("url");
      String rootType = jarDirectory.getAttributeValue("type");
      boolean recursive = Boolean.parseBoolean(jarDirectory.getAttributeValue("recursive"));
      jarDirectories.putValue(getRootType(rootType), url);
      if (recursive) {
        recursiveJarDirectories.putValue(getRootType(rootType), url);
      }
    }
    for (Element rootsElement : JDOMUtil.getChildren(libraryElement)) {
      final String rootTypeId = rootsElement.getName();
      if (!rootTypeId.equals("jarDirectory")) {
        final JpsOrderRootType rootType = getRootType(rootTypeId);
        for (Element rootElement : JDOMUtil.getChildren(rootsElement, "root")) {
          String url = rootElement.getAttributeValue("url");
          final boolean jarDirectory = jarDirectories.get(rootType).contains(url);
          final boolean recursive = recursiveJarDirectories.get(rootType).contains(url);
          library.addUrl(url, new JpsLibraryRootType(rootType, jarDirectory, recursive));
        }
      }
    }
    return library;
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

  private static JpsLibraryType<?> getLibraryType(String typeId) {
    return JpsJavaLibraryType.INSTANCE;
  }
}
