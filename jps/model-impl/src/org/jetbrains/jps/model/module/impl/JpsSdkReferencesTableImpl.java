package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;

/**
 * @author nik
 */
public class JpsSdkReferencesTableImpl extends JpsCompositeElementBase<JpsSdkReferencesTableImpl> implements JpsSdkReferencesTable {
  public static final JpsSdkReferencesTableRole ROLE = new JpsSdkReferencesTableRole();

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
    myContainer.setChild(new JpsSdkReferenceRole(type), sdkReference);
  }
  
  @Override
  public JpsLibraryReference getSdkReference(@NotNull JpsSdkType<?> type) {
    return myContainer.getChild(new JpsSdkReferenceRole(type));
  }

  private static class JpsSdkReferenceRole extends JpsElementChildRoleBase<JpsLibraryReference> {
    private final JpsSdkType<?> myType;

    private JpsSdkReferenceRole(@NotNull JpsSdkType<?> type) {
      super("sdk reference " + type);
      myType = type;
    }

    @Override
    public int hashCode() {
      return myType.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof JpsSdkReferenceRole && myType.equals(((JpsSdkReferenceRole)obj).myType);
    }
  }

  private static class JpsSdkReferencesTableRole extends JpsElementChildRoleBase<JpsSdkReferencesTable> implements JpsElementCreator<JpsSdkReferencesTable> {
    public JpsSdkReferencesTableRole() {
      super("sdk references");
    }

    @NotNull
    @Override
    public JpsSdkReferencesTable create() {
      return new JpsSdkReferencesTableImpl();
    }
  }
}
