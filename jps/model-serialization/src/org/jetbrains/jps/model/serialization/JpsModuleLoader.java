package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.service.JpsServiceManager;

import static com.intellij.openapi.util.JDOMUtil.getChildren;

/**
 * @author nik
 */
public class JpsModuleLoader {
  private static final String URL_ATTRIBUTE = "url";

  public static void loadRootModel(JpsModule module, Element rootModelComponent) {
    for (Element contentElement : getChildren(rootModelComponent, "content")) {
      final String url = contentElement.getAttributeValue(URL_ATTRIBUTE);
      module.getContentRootsList().addUrl(url);
      for (Element sourceElement : getChildren(contentElement, "sourceFolder")) {
        final String sourceUrl = sourceElement.getAttributeValue(URL_ATTRIBUTE);
        final String packagePrefix = sourceElement.getAttributeValue("packagePrefix");
        final boolean testSource = Boolean.parseBoolean(sourceElement.getAttributeValue("isTestSource"));
        final JavaSourceRootType rootType = testSource ? JavaSourceRootType.SOURCE : JavaSourceRootType.TEST_SOURCE;
        module.addSourceRoot(rootType, sourceUrl, new JavaSourceRootProperties(packagePrefix));
      }
      for (Element excludeElement : getChildren(contentElement, "excludeFolder")) {
        module.getExcludeRootsList().addUrl(excludeElement.getAttributeValue(URL_ATTRIBUTE));
      }
    }

    final JpsDependenciesList dependenciesList = module.getDependenciesList();
    final JpsElementFactory elementFactory = JpsElementFactory.getInstance();
    int moduleLibraryNum = 0;
    for (Element orderEntry : getChildren(rootModelComponent, "orderEntry")) {
      String type = orderEntry.getAttributeValue("type");
      if ("sourceFolder".equals(type)) {
        dependenciesList.addModuleSourceDependency();
      }
      else if ("jdk".equals(type)) {
        String sdkName = orderEntry.getAttributeValue("jdkName");
        String sdkTypeId = orderEntry.getAttributeValue("jskType");
        final JpsSdkType<?> sdkType = getSdkType(sdkTypeId);
        dependenciesList.addSdkDependency(sdkType);
        module.getSdkReferencesTable()
          .setSdkReference(sdkType, elementFactory.createLibraryReference(sdkName, elementFactory.createGlobalReference()));
      }
      else if ("inheritedJdk".equals(type)) {
        dependenciesList.addSdkDependency(JpsJavaSdkType.INSTANCE);
      }
      else if ("library".equals(type)) {
        String name = orderEntry.getAttributeValue("name");
        String level = orderEntry.getAttributeValue("level");
        final JpsLibraryDependency dependency =
          dependenciesList.addLibraryDependency(elementFactory.createLibraryReference(name, createLibraryTableReference(level)));
        loadModuleDependencyProperties(dependency, orderEntry);
      }
      else if ("module-library".equals(type)) {
        final Element moduleLibraryElement = orderEntry.getChild("library");
        final JpsLibrary library = JpsLibraryTableLoader.loadLibrary(moduleLibraryElement);
        module.addModuleLibrary(library);

        final JpsLibraryDependency dependency = dependenciesList.addLibraryDependency(library);
        loadModuleDependencyProperties(dependency, orderEntry);
        moduleLibraryNum++;
      }
      else if ("module".equals(type)) {
        String name = orderEntry.getAttributeValue("module-name");
        final JpsModuleDependency dependency = dependenciesList.addModuleDependency(elementFactory.createModuleReference(name));
        loadModuleDependencyProperties(dependency, orderEntry);
      }
    }

    for (JpsModelLoaderExtension extension : getLoaderExtensions()) {
      extension.loadRootModel(module, rootModelComponent);
    }
  }

  private static void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    for (JpsModelLoaderExtension extension : getLoaderExtensions()) {
      extension.loadModuleDependencyProperties(dependency, orderEntry);
    }
  }

  private static JpsElementReference<? extends JpsCompositeElement> createLibraryTableReference(String level) {
    JpsElementFactory elementFactory = JpsElementFactory.getInstance();
    if (level.equals("project")) {
      return elementFactory.createProjectReference();
    }
    if (level.equals("application")) {
      return elementFactory.createGlobalReference();
    }
    for (JpsModelLoaderExtension extension : getLoaderExtensions()) {
      final JpsElementReference<? extends JpsCompositeElement> reference = extension.createLibraryTableReference(level);
      if (reference != null) {
        return reference;
      }
    }
    throw new UnsupportedOperationException();
  }

  private static Iterable<JpsModelLoaderExtension> getLoaderExtensions() {
    return JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class);
  }

  public static JpsSdkType<?> getSdkType(String typeId) {
    for (JpsModelLoaderExtension extension : getLoaderExtensions()) {
      final JpsSdkType<?> type = extension.getSdkType(typeId);
      if (type != null) {
        return type;
      }
    }
    return JpsJavaSdkType.INSTANCE;
  }
}
