package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.serialization.module.JpsModuleSerializationDataExtension;

import java.io.File;

/**
 * @author nik
 */
public class JpsModuleSerializationDataExtensionImpl extends JpsElementBase<JpsModuleSerializationDataExtensionImpl> implements
                                                                                                                     JpsModuleSerializationDataExtension {
  public static final JpsElementChildRole<JpsModuleSerializationDataExtension> ROLE = JpsElementChildRoleBase.create("module serialization data");
  private File myBaseDirectory;

  public JpsModuleSerializationDataExtensionImpl(File baseDirectory) {
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
    return myBaseDirectory;
  }
}
