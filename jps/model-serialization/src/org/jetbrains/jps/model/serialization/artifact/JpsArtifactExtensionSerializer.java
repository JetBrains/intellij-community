package org.jetbrains.jps.model.serialization.artifact;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;

/**
 * @author nik
 */
public abstract class JpsArtifactExtensionSerializer<E extends JpsElement> {
  private JpsElementChildRole<E> myRole;
  private String myId;

  protected JpsArtifactExtensionSerializer(String id, JpsElementChildRole<E> role) {
    myId = id;
    myRole = role;
  }

  public JpsElementChildRole<E> getRole() {
    return myRole;
  }

  public String getId() {
    return myId;
  }

  public abstract E loadExtension(@Nullable Element optionsTag);

  public abstract void saveExtension(@NotNull E extension, @NotNull Element optionsTag);
}
