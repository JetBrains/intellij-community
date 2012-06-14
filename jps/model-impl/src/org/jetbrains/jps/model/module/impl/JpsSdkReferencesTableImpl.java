package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;

/**
 * @author nik
 */
public class JpsSdkReferencesTableImpl extends JpsCompositeElementBase<JpsSdkReferencesTableImpl> implements JpsSdkReferencesTable {
  public static final JpsElementKind<JpsSdkReferencesTableImpl> KIND = new JpsElementKindBase<JpsSdkReferencesTableImpl>("sdk references");

  public JpsSdkReferencesTableImpl() {
    super();
  }

  private JpsSdkReferencesTableImpl(JpsSdkReferencesTableImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsSdkReferencesTableImpl createCopy() {
    return new JpsSdkReferencesTableImpl(this);
  }

  @Override
  public void setSdkReference(@NotNull JpsSdkType<?> type, @NotNull JpsLibraryReference sdkReference) {
    myContainer.setChild(new JpsSdkReferenceKind(type), sdkReference);
  }
  
  @Override
  public JpsLibraryReference getSdkReference(@NotNull JpsSdkType<?> type) {
    return myContainer.getChild(new JpsSdkReferenceKind(type));
  }

  private static class JpsSdkReferenceKind extends JpsElementKindBase<JpsLibraryReference> {
    private final JpsSdkType<?> myType;

    private JpsSdkReferenceKind(@NotNull JpsSdkType<?> type) {
      super("sdk reference " + type);
      myType = type;
    }

    @Override
    public int hashCode() {
      return myType.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof JpsSdkReferenceKind && myType.equals(((JpsSdkReferenceKind)obj).myType);
    }
  }
}
