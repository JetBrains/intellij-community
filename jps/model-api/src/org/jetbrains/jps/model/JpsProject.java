/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;
import org.jetbrains.jps.model.module.JpsTypedModule;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfiguration;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;
import org.jetbrains.jps.model.runConfiguration.JpsTypedRunConfiguration;

import java.util.List;

/**
 * @author nik
 */
public interface JpsProject extends JpsCompositeElement, JpsReferenceableElement<JpsProject> {

  @NotNull
  <P extends JpsElement, ModuleType extends JpsModuleType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsModule addModule(@NotNull String name, @NotNull ModuleType moduleType);

  void addModule(@NotNull JpsModule module);

  void removeModule(@NotNull JpsModule module);

  @NotNull
  List<JpsModule> getModules();

  @NotNull
  <P extends JpsElement>
  Iterable<JpsTypedModule<P>> getModules(JpsModuleType<P> type);

  @NotNull
  <P extends JpsElement, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull String name, @NotNull LibraryType libraryType);

  @NotNull
  JpsLibraryCollection getLibraryCollection();

  @NotNull
  JpsSdkReferencesTable getSdkReferencesTable();

  @NotNull
  <P extends JpsElement>
  Iterable<JpsTypedRunConfiguration<P>> getRunConfigurations(JpsRunConfigurationType<P> type);

  @NotNull
  List<JpsRunConfiguration> getRunConfigurations();

  @NotNull
  <P extends JpsElement>
  JpsTypedRunConfiguration<P> addRunConfiguration(@NotNull String name, @NotNull JpsRunConfigurationType<P> type, @NotNull P properties);

  @NotNull String getName();

  void setName(@NotNull String name);

  @NotNull
  JpsModel getModel();
}
