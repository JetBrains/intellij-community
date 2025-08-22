// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.impl.compiler.JpsJavaCompilerConfigurationImpl;
import org.jetbrains.jps.model.java.impl.runConfiguration.JpsApplicationRunConfigurationPropertiesImpl;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationProperties;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationState;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.module.impl.JpsTestModulePropertiesImpl;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class JpsJavaExtensionServiceImpl extends JpsJavaExtensionService {
  @Override
  public @NotNull JpsJavaProjectExtension getOrCreateProjectExtension(@NotNull JpsProject project) {
    return project.getContainer().getOrSetChild(JavaProjectExtensionRole.INSTANCE);
  }

  @Override
  public @Nullable JpsJavaProjectExtension getProjectExtension(@NotNull JpsProject project) {
    return project.getContainer().getChild(JavaProjectExtensionRole.INSTANCE);
  }

  @Override
  public @NotNull JpsJavaModuleExtension getOrCreateModuleExtension(@NotNull JpsModule module) {
    return module.getContainer().getOrSetChild(JavaModuleExtensionRole.INSTANCE);
  }

  @Override
  public @NotNull JpsJavaDependencyExtension getOrCreateDependencyExtension(@NotNull JpsDependencyElement dependency) {
    return dependency.getContainer().getOrSetChild(JpsJavaDependencyExtensionRole.INSTANCE);
  }

  @Override
  public JpsJavaDependencyExtension getDependencyExtension(@NotNull JpsDependencyElement dependency) {
    JpsModule module = dependency.getContainingModule();
    if (module.getProject() instanceof JpsJavaAwareProject) {
      return ((JpsJavaAwareProject)module.getProject()).getJavaDependencyExtension(dependency);
    }
    return dependency.getContainer().getChild(JpsJavaDependencyExtensionRole.INSTANCE);
  }

  @Override
  public @Nullable JpsJavaModuleExtension getModuleExtension(@NotNull JpsModule module) {
    if (module.getProject() instanceof JpsJavaAwareProject) {
      return ((JpsJavaAwareProject)module.getProject()).getJavaModuleExtension(module);
    }
    return module.getContainer().getChild(JavaModuleExtensionRole.INSTANCE);
  }

  @Override
  public @NotNull ExplodedDirectoryModuleExtension getOrCreateExplodedDirectoryExtension(@NotNull JpsModule module) {
    return module.getContainer().getOrSetChild(ExplodedDirectoryModuleExtensionImpl.ExplodedDirectoryModuleExtensionRole.INSTANCE);
  }

  @Override
  public @Nullable ExplodedDirectoryModuleExtension getExplodedDirectoryExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(ExplodedDirectoryModuleExtensionImpl.ExplodedDirectoryModuleExtensionRole.INSTANCE);
  }

  @Override
  public @NotNull List<JpsDependencyElement> getDependencies(JpsModule module, JpsJavaClasspathKind classpathKind, boolean exportedOnly) {
    final List<JpsDependencyElement> result = new ArrayList<>();
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
  public @Nullable File getOutputDirectory(JpsModule module, boolean forTests) {
    String url = getOutputUrl(module, forTests);
    return url != null ? JpsPathUtil.urlToFile(url) : null;
  }

  @Override
  public @Nullable Path getOutputDirectoryPath(JpsModule module, boolean forTests) {
    String url = getOutputUrl(module, forTests);
    return url != null ? JpsPathUtil.urlToNioPath(url) : null;
  }

  @Override
  public @Nullable Path findSourceFile(@NotNull JpsModuleSourceRoot root, @NotNull String relativePath) {
    var properties = root.getProperties();
    var prefix = 
      properties instanceof JavaSourceRootProperties ? ((JavaSourceRootProperties)properties).getPackagePrefix().replace('.', '/') :
      properties instanceof JavaResourceRootProperties ? ((JavaResourceRootProperties)properties).getRelativeOutputPath() : "";

    var normalizedPrefix = StringUtil.trimStart(prefix, "/");
    if (!normalizedPrefix.isEmpty() && !normalizedPrefix.endsWith("/")) {
      normalizedPrefix += "/";
    }

    var normalizedRelativePath = StringUtil.trimStart(relativePath, "/");
    if (normalizedRelativePath.startsWith(normalizedPrefix)) {
      var result = root.getPath().resolve(normalizedRelativePath.substring(normalizedPrefix.length()));
      if (Files.exists(result)) {
        return result;
      }
    }
    return null;
  }

  @Override
  public @Nullable Path findSourceFileInProductionRoots(@NotNull JpsModule module, @NotNull String relativePath) {
    for (JpsModuleSourceRoot root : module.getSourceRoots()) {
      if (!root.getRootType().isForTests()) {
        Path file = findSourceFile(root, relativePath);
        if (file != null) {
          return file;
        }
      }
    }
    return null;
  }

  @Override
  public JpsTypedLibrary<JpsSdk<JpsDummyElement>> addJavaSdk(@NotNull JpsGlobal global, @NotNull String name, @NotNull String homePath) {
    JdkVersionDetector.JdkVersionInfo jdkInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(homePath);
    assert jdkInfo != null : homePath;
    String version = JdkVersionDetector.formatVersionString(jdkInfo.version);
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> sdk = global.addSdk(name, homePath, version, JpsJavaSdkType.INSTANCE);
    List<Path> roots = JavaSdkUtil.getJdkClassesRoots(Path.of(homePath), false);
    for (Path root : roots) {
      sdk.addRoot(root, JpsOrderRootType.COMPILED);
    }
    return sdk;
  }

  @Override
  public @NotNull JpsJavaCompilerConfiguration getCompilerConfiguration(@NotNull JpsProject project) {
    return project.getContainer().getOrSetChild(JpsJavaCompilerConfigurationImpl.ROLE);
  }

  @Override
  public @Nullable JpsTestModuleProperties getTestModuleProperties(@NotNull JpsModule module) {
    if (module.getProject() instanceof JpsJavaAwareProject) {
      return ((JpsJavaAwareProject)module.getProject()).getTestModuleProperties(module);
    }
    return module.getContainer().getChild(JpsTestModulePropertiesImpl.ROLE);
  }

  @Override
  public void setTestModuleProperties(@NotNull JpsModule module, @NotNull JpsModuleReference productionModuleReference) {
    module.getContainer().setChild(JpsTestModulePropertiesImpl.ROLE, new JpsTestModulePropertiesImpl(productionModuleReference));
  }

  @Override
  public @NotNull JpsSdkReference<JpsDummyElement> createWrappedJavaSdkReference(@NotNull JpsJavaSdkTypeWrapper sdkType,
                                                                                 @NotNull JpsSdkReference<?> wrapperReference) {
    return new JpsWrappedJavaSdkReferenceImpl(sdkType, wrapperReference);
  }

  @Override
  public @NotNull JpsApplicationRunConfigurationProperties createRunConfigurationProperties(JpsApplicationRunConfigurationState state) {
    return new JpsApplicationRunConfigurationPropertiesImpl(state);
  }

  @Override
  public @NotNull JavaSourceRootProperties createSourceRootProperties(@NotNull String packagePrefix, boolean isGenerated) {
    return new JavaSourceRootProperties(packagePrefix, isGenerated);
  }

  @Override
  public @NotNull JavaSourceRootProperties createSourceRootProperties(@NotNull String packagePrefix) {
    return createSourceRootProperties(packagePrefix, false);
  }

  @Override
  public @NotNull JavaResourceRootProperties createResourceRootProperties(@NotNull String relativeOutputPath, boolean forGeneratedResource) {
    return new JavaResourceRootProperties(relativeOutputPath, forGeneratedResource);
  }

  @Override
  public @NotNull JpsProductionModuleOutputPackagingElement createProductionModuleOutput(@NotNull JpsModuleReference moduleReference) {
    return new JpsProductionModuleOutputPackagingElementImpl(moduleReference);
  }

  @Override
  public @NotNull JpsProductionModuleSourcePackagingElement createProductionModuleSource(@NotNull JpsModuleReference moduleReference) {
    return new JpsProductionModuleSourcePackagingElementImpl(moduleReference);
  }

  @Override
  public @NotNull JpsTestModuleOutputPackagingElement createTestModuleOutput(@NotNull JpsModuleReference moduleReference) {
    return new JpsTestModuleOutputPackagingElementImpl(moduleReference);
  }

  @Override
  public JpsJavaDependenciesEnumerator enumerateDependencies(Collection<JpsModule> modules) {
    return new JpsJavaDependenciesEnumeratorImpl(modules);
  }

  @Override
  protected JpsJavaDependenciesEnumerator enumerateDependencies(JpsProject project) {
    return new JpsJavaDependenciesEnumeratorImpl(project.getModules());
  }

  @Override
  protected JpsJavaDependenciesEnumerator enumerateDependencies(JpsModule module) {
    return new JpsJavaDependenciesEnumeratorImpl(Collections.singleton(module));
  }

  @Override
  public @NotNull JavaModuleIndex getJavaModuleIndex(@NotNull JpsProject project) {
    return project.getContainer().getOrSetChild(JavaModuleIndexRole.INSTANCE, () -> getCompilerConfiguration(project).getCompilerExcludes());
  }
}
