// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.workspaceModel.ide.legacyBridge.impl.java.JavaModuleTypeUtils.JAVA_MODULE_ENTITY_TYPE_ID_NAME;

public class DefaultLightProjectDescriptor extends LightProjectDescriptor {
  private static final String JETBRAINS_ANNOTATIONS_COORDINATES = "org.jetbrains:annotations-java5:24.0.0";
  private static final String JETBRAINS_ANNOTATIONS_COORDINATES_JAVA_8 = "org.jetbrains:annotations:26.0.2";
  private @Nullable Supplier<? extends Sdk> customSdk;
  private final List<RequiredLibrary> mavenLibraries = new ArrayList<>();

  public DefaultLightProjectDescriptor() {
  }

  public DefaultLightProjectDescriptor(@NotNull Supplier<? extends Sdk> customSdk) {
    this.customSdk = customSdk;
  }

  public DefaultLightProjectDescriptor(@NotNull Supplier<? extends Sdk> customSdk, @NotNull List<String> mavenLibraries) {
    this.customSdk = customSdk;
    for (String library : mavenLibraries) {
      withRepositoryLibrary(library);
    }
  }

  @Override
  public @NotNull String getModuleTypeId() {
    return JAVA_MODULE_ENTITY_TYPE_ID_NAME;
  }

  @Override
  public Sdk getSdk() {
    return customSdk == null ? IdeaTestUtil.getMockJdk17() : customSdk.get();
  }

  @Override
  public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
    LanguageLevelModuleExtension extension = model.getModuleExtension(LanguageLevelModuleExtension.class);
    if (extension != null) {
      extension.setLanguageLevel(LanguageLevel.HIGHEST);

      for (RequiredLibrary library : mavenLibraries) {
        MavenDependencyUtil.addFromMaven(model, library.mavenCoordinates, library.includeTransitive);
      }
    }
  }

  public DefaultLightProjectDescriptor withRepositoryLibrary(@NotNull String library) {
    return withRepositoryLibrary(library, true);
  }

  public DefaultLightProjectDescriptor withRepositoryLibrary(@NotNull String library, boolean includeTransitive) {
    mavenLibraries.add(new RequiredLibrary(library, includeTransitive));
    return this;
  }
  
  public DefaultLightProjectDescriptor withJetBrainsAnnotations() {
    return withRepositoryLibrary(JETBRAINS_ANNOTATIONS_COORDINATES);
  }

  public DefaultLightProjectDescriptor withJetBrainsAnnotationsWithTypeUse() {
    return withRepositoryLibrary(JETBRAINS_ANNOTATIONS_COORDINATES_JAVA_8);
  }

  public static void addJetBrainsAnnotationsWithTypeUse(ModifiableRootModel model) {
    MavenDependencyUtil.addFromMaven(model, JETBRAINS_ANNOTATIONS_COORDINATES_JAVA_8);
  }

  /**
   * Adds old non-type-use JetBrains annotations as a module dependency.
   * Currently, many tests assume that this dependency is present.
   * 
   * @param model model to modify
   */
  public static void addJetBrainsAnnotations(@NotNull ModifiableRootModel model) {
    MavenDependencyUtil.addFromMaven(model, JETBRAINS_ANNOTATIONS_COORDINATES);
  }

  private static class RequiredLibrary {
    public final String mavenCoordinates;
    public final boolean includeTransitive;

    private RequiredLibrary(String coordinates, boolean transitive) {
      mavenCoordinates = coordinates;
      includeTransitive = transitive;
    }
  }
}
