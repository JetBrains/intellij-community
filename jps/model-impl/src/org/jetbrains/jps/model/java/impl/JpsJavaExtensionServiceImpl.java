package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsJavaExtensionServiceImpl extends JpsJavaExtensionService {
  @NotNull
  @Override
  public JpsJavaProjectExtension getOrCreateProjectExtension(@NotNull JpsProject project) {
    return project.getContainer().getOrSetChild(JavaProjectExtensionKind.INSTANCE);
  }

  @Nullable
  @Override
  public JpsJavaProjectExtension getProjectExtension(@NotNull JpsProject project) {
    return project.getContainer().getChild(JavaProjectExtensionKind.INSTANCE);
  }

  @NotNull
  @Override
  public JpsJavaModuleExtension getOrCreateModuleExtension(@NotNull JpsModule module) {
    return module.getContainer().getOrSetChild(JavaModuleExtensionKind.INSTANCE);
  }

  @NotNull
  @Override
  public JpsJavaDependencyExtension getOrCreateDependencyExtension(@NotNull JpsDependencyElement dependency) {
    return dependency.getContainer().getOrSetChild(JpsJavaDependencyExtensionKind.INSTANCE);
  }

  @Override
  public JpsJavaDependencyExtension getDependencyExtension(@NotNull JpsDependencyElement dependency) {
    return dependency.getContainer().getChild(getDependencyExtensionKind());
  }

  @Override
  @Nullable
  public JpsJavaModuleExtension getModuleExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(getModuleExtensionKind());
  }

  @NotNull
  @Override
  public JpsElementKind<JpsJavaModuleExtension> getModuleExtensionKind() {
    return JavaModuleExtensionKind.INSTANCE;
  }

  @NotNull
  @Override
  public JpsElementKind<JpsJavaDependencyExtension> getDependencyExtensionKind() {
    return JpsJavaDependencyExtensionKind.INSTANCE;
  }

  @Override
  @NotNull
  public ExplodedDirectoryModuleExtension getOrCreateExplodedDirectoryExtension(@NotNull JpsModule module) {
    ExplodedDirectoryModuleExtension extension = module.getContainer().getChild(ExplodedDirectoryModuleExtensionImpl.KIND);
    if (extension == null) {
      extension = module.getContainer().setChild(ExplodedDirectoryModuleExtensionImpl.KIND, new ExplodedDirectoryModuleExtensionImpl());
    }
    return extension;
  }

  @NotNull
  @Override
  public List<JpsDependencyElement> getDependencies(JpsModule module, JpsJavaClasspathKind classpathKind, boolean exportedOnly) {
    final List<JpsDependencyElement> result = new ArrayList<JpsDependencyElement>();
    for (JpsDependencyElement dependencyElement : module.getDependenciesList().getDependencies()) {
      final JpsJavaDependencyExtension extension = getDependencyExtension(dependencyElement);
      if (extension == null || extension.getScope().isIncludedIn(classpathKind) && (!exportedOnly || extension.isExported())) {
        result.add(dependencyElement);
      }
    }
    return result;
  }

  @Override
  public LanguageLevel getLanguageLevel(JpsModule module) {
    final JpsJavaModuleExtension moduleExtension = getModuleExtension(module);
    if (moduleExtension == null) return null;
    final LanguageLevel languageLevel = moduleExtension.getLanguageLevel();
    if (languageLevel != null) return languageLevel;
    final JpsJavaProjectExtension projectExtension = getProjectExtension(module.getProject());
    return projectExtension != null ? projectExtension.getLanguageLevel() : null;
  }

  @Override
  public String getOutputUrl(JpsModule module, boolean forTests) {
    final JpsJavaModuleExtension extension = getModuleExtension(module);
    if (extension == null) return null;
    if (extension.isInheritOutput()) {
      JpsJavaProjectExtension projectExtension = getProjectExtension(module.getProject());
      if (projectExtension == null) return null;
      final String url = projectExtension.getOutputUrl();
      if (url == null) return null;
      return url + "/" + (forTests ? "test" : "production") + "/" + module.getName();
    }
    return forTests ? extension.getTestOutputUrl() : extension.getOutputUrl();
  }

  @Override
  public String getSourcePrefix(JpsModule module, String rootUrl) {
    for (JpsModuleSourceRoot root : module.getSourceRoots()) {
      if (root.getUrl().equals(rootUrl)) {
        final JpsElementProperties properties = root.getProperties();
        if (properties instanceof JavaSourceRootProperties) {
          return ((JavaSourceRootProperties)properties).getPackagePrefix();
        }
      }
    }
    return null;
  }
}
