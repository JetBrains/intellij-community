// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.serialization.JpsProjectSerializationDataExtension;

import java.io.File;
import java.nio.file.Path;

public final class JpsProjectSerializationDataExtensionImpl extends JpsElementBase<JpsProjectSerializationDataExtensionImpl> implements JpsProjectSerializationDataExtension {
  public static final JpsElementChildRole<JpsProjectSerializationDataExtension> ROLE = JpsElementChildRoleBase.create("serialization data");
  private final Path myBaseDirectory;

  public JpsProjectSerializationDataExtensionImpl(@NotNull Path baseDirectory) {
    myBaseDirectory = baseDirectory;
  }

  @NotNull
  @Override
  public JpsProjectSerializationDataExtensionImpl createCopy() {
    return new JpsProjectSerializationDataExtensionImpl(myBaseDirectory);
  }

  @NotNull
  @Override
  public File getBaseDirectory() {
    return myBaseDirectory.toFile();
  }
}
