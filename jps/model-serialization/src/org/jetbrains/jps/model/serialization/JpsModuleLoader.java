package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
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

  public static void loadRootModel(JpsModule module, Element rootModelComponent, JpsSdkType<?> projectSdkType) {
    for (Element contentElement : getChildren(rootModelComponent, "content")) {
      final String url = contentElement.getAttributeValue(URL_ATTRIBUTE);
      module.getContentRootsList().addUrl(url);
      for (Element sourceElement : getChildren(contentElement, "sourceFolder")) {
        final String sourceUrl = sourceElement.getAttributeValue(URL_ATTRIBUTE);
        final String packagePrefix = StringUtil.notNullize(sourceElement.getAttributeValue("packagePrefix"));
        final boolean testSource = Boolean.parseBoolean(sourceElement.getAttributeValue("isTestSource"));
        final JavaSourceRootType rootType = testSource ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
        module.addSourceRoot(sourceUrl, rootType, new JavaSourceRootProperties(packagePrefix));
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
        String sdkTypeId = orderEntry.getAttributeValue("jdkType");
        final JpsSdkType<?> sdkType = JpsSdkTableLoader.getSdkType(sdkTypeId);
        dependenciesList.addSdkDependency(sdkType);
        JpsSdkTableLoader.setSdkReference(module.getSdkReferencesTable(), sdkName, sdkType);
      }
      else if ("inheritedJdk".equals(type)) {
        dependenciesList.addSdkDependency(projectSdkType != null ? projectSdkType : JpsJavaSdkType.INSTANCE);
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
        String name = moduleLibraryElement.getAttributeValue("name");
        if (name == null) {
          name = "#" + (moduleLibraryNum++);
        }
        final JpsLibrary library = JpsLibraryTableLoader.loadLibrary(moduleLibraryElement, name);
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

  public static JpsElementReference<? extends JpsCompositeElement> createLibraryTableReference(String level) {
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
}
