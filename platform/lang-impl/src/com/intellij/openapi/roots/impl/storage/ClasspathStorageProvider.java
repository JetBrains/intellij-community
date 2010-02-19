/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.Set;

/**
 * @author Vladislav.Kaznacheev
 */
public interface ClasspathStorageProvider {
  @NonNls ExtensionPointName<ClasspathStorageProvider> EXTENSION_POINT_NAME =
    new ExtensionPointName<ClasspathStorageProvider>("com.intellij.classpathStorageProvider");

  @NonNls
  String getID();

  @Nls
  String getDescription();

  void assertCompatible(final ModifiableRootModel model) throws ConfigurationException;

  void detach(Module module);

  void moduleRenamed(Module module, String newName);

  ClasspathConverter createConverter(Module module);

  interface ClasspathConverter {

    FileSet getFileSet();

    Set<String> getClasspath(ModifiableRootModel model, final Element element) throws IOException, InvalidDataException;

    void setClasspath(ModuleRootModel model) throws IOException, WriteExternalException;
  }
}
