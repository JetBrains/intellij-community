package org.jetbrains.jps.model.serialization.java;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsUrlList;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelLoaderExtension;

/**
 * @author nik
 */
public class JpsJavaModelLoaderExtension extends JpsModelLoaderExtension {
  @Override
  public void loadRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
    loadExplodedDirectoryExtension(module, rootModel);
    loadJavaModuleExtension(module, rootModel);
  }

  @Override
  public void loadProjectRoots(JpsProject project, Element rootManagerElement) {
    loadJavaProjectExtensions(project, rootManagerElement);
  }

  private static void loadJavaProjectExtensions(JpsProject project, Element rootManagerElement) {
    JpsJavaProjectExtension extension = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project);
    final Element output = rootManagerElement.getChild("output");
    if (output != null) {
      String url = output.getAttributeValue("url");
      if (url != null) {
        extension.setOutputUrl(url);
      }
    }
    String languageLevel = rootManagerElement.getAttributeValue("languageLevel");
    if (languageLevel != null) {
      extension.setLanguageLevel(LanguageLevel.valueOf(languageLevel));
    }
  }

  @Override
  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element entry) {
    boolean exported = entry.getAttributeValue("exported") != null;
    String scopeName = entry.getAttributeValue("scope");
    JpsJavaDependencyScope scope = scopeName != null ? JpsJavaDependencyScope.valueOf(scopeName) : JpsJavaDependencyScope.COMPILE;

    final JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency);
    extension.setExported(exported);
    extension.setScope(scope);
  }

  @Override
  public JpsOrderRootType getRootType(@NotNull String typeId) {
    if (typeId.equals("JAVADOC")) {
      return JpsOrderRootType.DOCUMENTATION;
    }
    else if (typeId.equals("ANNOTATIONS")) {
      return JpsAnnotationRootType.INSTANCE;
    }
    return null;
  }

  @Override
  public JpsOrderRootType getSdkRootType(@NotNull String typeId) {
    if (typeId.equals("javadocPath")) {
      return JpsOrderRootType.DOCUMENTATION;
    }
    if (typeId.equals("annotationsPath")) {
      return JpsAnnotationRootType.INSTANCE;
    }
    return null;
  }

  private static void loadExplodedDirectoryExtension(JpsModule module, Element rootModelComponent) {
    final Element exploded = rootModelComponent.getChild("exploded");
    if (exploded != null) {
      final ExplodedDirectoryModuleExtension extension =
        JpsJavaExtensionService.getInstance().getOrCreateExplodedDirectoryExtension(module);
      extension.setExcludeExploded(rootModelComponent.getChild("exclude-exploded") != null);
      extension.setExplodedUrl(exploded.getAttributeValue("url"));
    }
  }

  private static void loadJavaModuleExtension(JpsModule module, Element rootModelComponent) {
    final JpsJavaModuleExtension extension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module);
    final Element outputTag = rootModelComponent.getChild("output");
    if (outputTag != null) {
      extension.setOutputUrl(outputTag.getAttributeValue("url"));
    }
    final Element testOutputTag = rootModelComponent.getChild("output-test");
    if (testOutputTag != null) {
      extension.setOutputUrl(testOutputTag.getAttributeValue("url"));
    }
    extension.setInheritOutput(Boolean.parseBoolean(rootModelComponent.getAttributeValue("inherit-compiler-output")));
    extension.setExcludeOutput(rootModelComponent.getChild("exclude-output") != null);

    loadAdditionalRoots(rootModelComponent, "annotation-paths", extension.getAnnotationRoots());
    loadAdditionalRoots(rootModelComponent, "javadoc-paths", extension.getJavadocRoots());

    final String languageLevel = rootModelComponent.getAttributeValue("LANGUAGE_LEVEL");
    if (languageLevel != null) {
      extension.setLanguageLevel(LanguageLevel.valueOf(languageLevel));
    }
  }

  private static void loadAdditionalRoots(Element rootModelComponent, final String rootsTagName, final JpsUrlList result) {
    final Element roots = rootModelComponent.getChild(rootsTagName);
    for (Element root : JDOMUtil.getChildren(roots, "root")) {
      result.addUrl(root.getAttributeValue("url"));
    }
  }
}
