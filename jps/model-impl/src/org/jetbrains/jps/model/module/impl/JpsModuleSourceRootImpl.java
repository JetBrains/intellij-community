package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsTypedDataImpl;
import org.jetbrains.jps.model.impl.JpsTypedDataKind;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * @author nik
 */
public class JpsModuleSourceRootImpl extends JpsCompositeElementBase<JpsModuleSourceRootImpl> implements JpsModuleSourceRoot {
  private static final JpsTypedDataKind<JpsModuleSourceRootType> TYPED_DATA_KIND = new JpsTypedDataKind<JpsModuleSourceRootType>();
  private String myUrl;

  public JpsModuleSourceRootImpl(JpsModel model, JpsEventDispatcher eventDispatcher,
                                 String url,
                                 JpsModuleSourceRootType type, JpsParentElement parent) {
    super(model, eventDispatcher, parent);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsModuleSourceRootType>(type, eventDispatcher, this));
    myUrl = url;
  }

  public JpsModuleSourceRootImpl(JpsModuleSourceRootImpl original, JpsModel model, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, model, dispatcher, parent);
    myUrl = original.myUrl;
  }

  @Override
  public <P extends JpsElementProperties> P getProperties(@NotNull JpsModuleSourceRootType<P> type) {
    return myContainer.getChild(TYPED_DATA_KIND).getProperties(type);
  }

  @Override
  public <P extends JpsElementProperties> void setProperties(JpsModuleSourceRootType<P> type, P properties) {
    myContainer.getChild(TYPED_DATA_KIND).setProperties(properties);
  }

  @NotNull
  @Override
  public JpsModuleSourceRootType<?> getRootType() {
    return myContainer.getChild(TYPED_DATA_KIND).getType();
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  @Override
  public JpsModuleSourceRootImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsModuleSourceRootImpl(this, model, eventDispatcher, parent);
  }
}
