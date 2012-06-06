package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;

/**
 * @author nik
 */
public class JpsSdkReferencesTableImpl extends JpsCompositeElementBase<JpsSdkReferencesTableImpl> implements JpsSdkReferencesTable {
  public static final JpsElementKind<JpsSdkReferencesTableImpl> KIND = new JpsElementKind<JpsSdkReferencesTableImpl>();

  public JpsSdkReferencesTableImpl(JpsModel model, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(model, eventDispatcher, parent);
  }

  public JpsSdkReferencesTableImpl(JpsSdkReferencesTableImpl original, JpsModel model, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, model, dispatcher, parent);
  }

  @NotNull
  @Override
  public JpsSdkReferencesTableImpl createCopy(@NotNull JpsModel model,
                                              @NotNull JpsEventDispatcher eventDispatcher,
                                              JpsParentElement parent) {
    return new JpsSdkReferencesTableImpl(this, model, eventDispatcher, parent);
  }

  @Override
  public void setSdkReference(@NotNull JpsSdkType<?> type, @NotNull JpsLibrary sdk) {
    myContainer.setChild(new JpsSdkReferenceKind(type), sdk.createReference(this));
  }
  
  @Override
  public JpsLibraryReference getSdkReference(@NotNull JpsSdkType<?> type) {
    return myContainer.getChild(new JpsSdkReferenceKind(type));
  }

  private static class JpsSdkReferenceKind extends JpsElementKind<JpsLibraryReference> {
    private final JpsSdkType<?> myType;

    private JpsSdkReferenceKind(@NotNull JpsSdkType<?> type) {
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
