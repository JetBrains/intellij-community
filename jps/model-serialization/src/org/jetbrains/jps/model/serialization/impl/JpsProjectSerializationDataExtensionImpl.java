package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.serialization.JpsProjectSerializationDataExtension;

import java.io.File;

/**
 * @author nik
 */
public class JpsProjectSerializationDataExtensionImpl extends JpsElementBase<JpsProjectSerializationDataExtensionImpl> implements JpsProjectSerializationDataExtension {
  public static final JpsElementChildRole<JpsProjectSerializationDataExtension> ROLE = JpsElementChildRoleBase.create("serialization data");
  private File myBaseDirectory;

  public JpsProjectSerializationDataExtensionImpl(File baseDirectory) {
    myBaseDirectory = baseDirectory;
  }

  @NotNull
  @Override
  public JpsProjectSerializationDataExtensionImpl createCopy() {
    return new JpsProjectSerializationDataExtensionImpl(myBaseDirectory);
  }

  @Override
  public void applyChanges(@NotNull JpsProjectSerializationDataExtensionImpl modified) {
  }

  @NotNull
  @Override
  public File getBaseDirectory() {
    return myBaseDirectory;
  }
}
