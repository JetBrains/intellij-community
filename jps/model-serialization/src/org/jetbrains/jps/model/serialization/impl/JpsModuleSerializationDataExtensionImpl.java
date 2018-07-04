/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.serialization.module.JpsModuleSerializationDataExtension;

import java.io.File;
import java.nio.file.Path;

/**
 * @author nik
 */
public class JpsModuleSerializationDataExtensionImpl extends JpsElementBase<JpsModuleSerializationDataExtensionImpl> implements
                                                                                                                     JpsModuleSerializationDataExtension {
  public static final JpsElementChildRole<JpsModuleSerializationDataExtension> ROLE = JpsElementChildRoleBase.create("module serialization data");
  private final Path myBaseDirectory;

  public JpsModuleSerializationDataExtensionImpl(@NotNull Path baseDirectory) {
    myBaseDirectory = baseDirectory;
  }

  @NotNull
  @Override
  public JpsModuleSerializationDataExtensionImpl createCopy() {
    return new JpsModuleSerializationDataExtensionImpl(myBaseDirectory);
  }

  @Override
  public void applyChanges(@NotNull JpsModuleSerializationDataExtensionImpl modified) {
  }

  @NotNull
  @Override
  public File getBaseDirectory() {
    return myBaseDirectory.toFile();
  }
}
