package org.jetbrains.jps.model.serialization.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsOrderRootType;

/**
 * @author nik
 */
public class JpsLibraryRootTypeSerializer implements Comparable<JpsLibraryRootTypeSerializer> {
  private String myTypeId;
  private JpsOrderRootType myType;
  private boolean myWriteIfEmpty;

  public JpsLibraryRootTypeSerializer(@NotNull String typeId, @NotNull JpsOrderRootType type, boolean writeIfEmpty) {
    myTypeId = typeId;
    myType = type;
    myWriteIfEmpty = writeIfEmpty;
  }

  public boolean isWriteIfEmpty() {
    return myWriteIfEmpty;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public JpsOrderRootType getType() {
    return myType;
  }

  @Override
  public int compareTo(JpsLibraryRootTypeSerializer o) {
    return myTypeId.compareTo(o.myTypeId);
  }
}
