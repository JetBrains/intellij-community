/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleTypeId;
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

/**
 * @author peter
 */
public class DefaultLightProjectDescriptor extends LightProjectDescriptor {

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
    return ModuleTypeId.JAVA_MODULE;
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

  private static class RequiredLibrary {
    public final String mavenCoordinates;
    public final boolean includeTransitive;

    private RequiredLibrary(String coordinates, boolean transitive) {
      mavenCoordinates = coordinates;
      includeTransitive = transitive;
    }
  }
}
