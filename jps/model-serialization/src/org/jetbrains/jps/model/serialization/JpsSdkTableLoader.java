package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.JpsJavaSdkTypeWrapper;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsSdkTableLoader {
  private static final Map<String, JpsOrderRootType> PREDEFINED_ROOT_TYPES = new HashMap<String, JpsOrderRootType>();

  static {
    PREDEFINED_ROOT_TYPES.put("classPath", JpsOrderRootType.COMPILED);
    PREDEFINED_ROOT_TYPES.put("sourcePath", JpsOrderRootType.SOURCES);
  }

  public static void loadSdks(@Nullable Element sdkListElement, JpsLibraryCollection result) {
    for (Element sdkElement : JDOMUtil.getChildren(sdkListElement, "jdk")) {
      result.addLibrary(loadSdk(sdkElement));
    }
  }

  private static JpsLibrary loadSdk(Element sdkElement) {
    String name = getAttributeValue(sdkElement, "name");
    String typeId = getAttributeValue(sdkElement, "type");
    JpsSdkPropertiesLoader<?> loader = getSdkPropertiesLoader(typeId);
    final JpsLibrary library = createSdk(name, loader, sdkElement);
    final Element roots = sdkElement.getChild("roots");
    for (Element rootTypeElement : JDOMUtil.getChildren(roots)) {
      JpsOrderRootType rootType = getRootType(rootTypeElement.getName());
      if (rootType != null) {
        for (Element rootElement : JDOMUtil.getChildren(rootTypeElement)) {
          loadRoots(rootElement, library, rootType);
        }
      }
    }
    return library;
  }

  private static void loadRoots(Element rootElement, JpsLibrary library, JpsOrderRootType rootType) {
    final String type = rootElement.getAttributeValue("type");
    if (type.equals("composite")) {
      for (Element element : JDOMUtil.getChildren(rootElement)) {
        loadRoots(element, library, rootType);
      }
    }
    else if (type.equals("simple")) {
      library.addRoot(rootElement.getAttributeValue("url"), rootType);
    }
  }

  @Nullable
  private static JpsOrderRootType getRootType(String typeId) {
    final JpsOrderRootType rootType = PREDEFINED_ROOT_TYPES.get(typeId);
    if (rootType != null) {
      return rootType;
    }
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      final JpsOrderRootType type = extension.getSdkRootType(typeId);
      if (type != null) {
        return type;
      }
    }
    return null;
  }

  private static <P extends JpsSdkProperties> JpsLibrary createSdk(String name, JpsSdkPropertiesLoader<P> loader, Element sdkElement) {
    String versionString = getAttributeValue(sdkElement, "version");
    String homePath = getAttributeValue(sdkElement, "homePath");
    return JpsElementFactory.getInstance().createLibrary(name, loader.getType(), loader.loadProperties(homePath, versionString, sdkElement.getChild("additional")));
  }

  public static JpsSdkPropertiesLoader<?> getSdkPropertiesLoader(String typeId) {
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      for (JpsSdkPropertiesLoader<?> loader : extension.getSdkPropertiesLoaders()) {
        if (loader.getTypeId().equals(typeId)) {
          return loader;
        }
      }
    }
    return new JpsSdkPropertiesLoader<JpsSdkProperties>("JavaSDK", JpsJavaSdkType.INSTANCE) {
      @Override
      public JpsSdkProperties loadProperties(String homePath, String version, Element propertiesElement) {
        return new JpsSdkProperties(homePath, version);
      }
    };
  }

  @Nullable
  private static String getAttributeValue(Element element, String childName) {
    final Element child = element.getChild(childName);
    return child != null ? child.getAttributeValue("value") : null;
  }

  public static JpsSdkType<?> getSdkType(String typeId) {
    return getSdkPropertiesLoader(typeId).getType();
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
