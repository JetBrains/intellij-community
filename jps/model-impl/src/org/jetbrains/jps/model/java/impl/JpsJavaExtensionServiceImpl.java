package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsJavaExtensionServiceImpl extends JpsJavaExtensionService {
  @NotNull
  @Override
  public JpsJavaProjectExtension getOrCreateProjectExtension(@NotNull JpsProject project) {
    return project.getContainer().getOrSetChild(JavaProjectExtensionRole.INSTANCE);
  }

  @Nullable
  @Override
  public JpsJavaProjectExtension getProjectExtension(@NotNull JpsProject project) {
    return project.getContainer().getChild(JavaProjectExtensionRole.INSTANCE);
  }

  @NotNull
  @Override
  public JpsJavaModuleExtension getOrCreateModuleExtension(@NotNull JpsModule module) {
    return module.getContainer().getOrSetChild(JavaModuleExtensionRole.INSTANCE);
  }

  @NotNull
  @Override
  public JpsJavaDependencyExtension getOrCreateDependencyExtension(@NotNull JpsDependencyElement dependency) {
    return dependency.getContainer().getOrSetChild(JpsJavaDependencyExtensionRole.INSTANCE);
  }

  @Override
  public JpsJavaDependencyExtension getDependencyExtension(@NotNull JpsDependencyElement dependency) {
    return dependency.getContainer().getChild(JpsJavaDependencyExtensionRole.INSTANCE);
  }

  @Override
  @Nullable
  public JpsJavaModuleExtension getModuleExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JavaModuleExtensionRole.INSTANCE);
  }

  @Override
  @NotNull
  public ExplodedDirectoryModuleExtension getOrCreateExplodedDirectoryExtension(@NotNull JpsModule module) {
    return module.getContainer().getOrSetChild(ExplodedDirectoryModuleExtensionImpl.ExplodedDirectoryModuleExtensionRole.INSTANCE);
  }

  @Override
  @Nullable
  public ExplodedDirectoryModuleExtension getExplodedDirectoryExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(ExplodedDirectoryModuleExtensionImpl.ExplodedDirectoryModuleExtensionRole.INSTANCE);
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
        JpsModuleSourceRootType<?> type = root.getRootType();
        if (type instanceof JavaSourceRootType) {
          final JpsSimpleElement<JavaSourceRootProperties> properties = root.getProperties((JavaSourceRootType)type);
          if (properties != null) {
            return properties.getData().getPackagePrefix();
          }
        }
      }
    }
    return null;
  }

  @Override
  public JpsTypedLibrary<JpsSdk<JpsDummyElement>> addJavaSdk(@NotNull JpsGlobal global, @NotNull String name, @NotNull String homePath) {
    String version = JdkVersionDetector.getInstance().detectJdkVersion(homePath);
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> sdk = global.addSdk(name, homePath, version, JpsJavaSdkType.INSTANCE);
    File homeDir = new File(FileUtil.toSystemDependentName(homePath));
    List<File> roots = JavaSdkUtil.getJdkClassesRoots(homeDir, false);
    for (File root : roots) {
      sdk.addRoot(root, JpsOrderRootType.COMPILED);
    }
    return sdk;
  }

  @Override
  @NotNull
  public JpsProductionModuleOutputPackagingElement createProductionModuleOutput(@NotNull JpsModuleReference moduleReference) {
    return new JpsProductionModuleOutputPackagingElementImpl(moduleReference);
  }

  @Override
  @NotNull
  public JpsTestModuleOutputPackagingElement createTestModuleOutput(@NotNull JpsModuleReference moduleReference) {
    return new JpsTestModuleOutputPackagingElementImpl(moduleReference);
  }

  @Override
  protected JpsJavaDependenciesEnumerator enumerateDependencies(JpsModule module) {
    return new JpsJavaDependenciesEnumeratorImpl(Collections.singletonList(module));
  }
}
