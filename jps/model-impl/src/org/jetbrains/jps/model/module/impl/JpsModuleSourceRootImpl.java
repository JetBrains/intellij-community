package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsTypedDataImpl;
import org.jetbrains.jps.model.impl.JpsTypedDataRole;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * @author nik
 */
public class JpsModuleSourceRootImpl extends JpsCompositeElementBase<JpsModuleSourceRootImpl> implements JpsModuleSourceRoot {
  private static final JpsTypedDataRole<JpsModuleSourceRootType<?>> TYPED_DATA_ROLE = new JpsTypedDataRole<JpsModuleSourceRootType<?>>();
  private String myUrl;

  public <P extends JpsElementProperties> JpsModuleSourceRootImpl(String url, JpsModuleSourceRootType<P> type, P properties) {
    super();
    myContainer.setChild(TYPED_DATA_ROLE, new JpsTypedDataImpl<JpsModuleSourceRootType<?>>(type, properties));
    myUrl = url;
  }

  private JpsModuleSourceRootImpl(JpsModuleSourceRootImpl original) {
    super(original);
    myUrl = original.myUrl;
  }

  @Override
  public <P extends JpsElementProperties> P getProperties(@NotNull JpsModuleSourceRootType<P> type) {
    return myContainer.getChild(TYPED_DATA_ROLE).getProperties(type);
  }

  @NotNull
  @Override
  public JpsElementProperties getProperties() {
    return myContainer.getChild(TYPED_DATA_ROLE).getProperties();
  }

  @Override
  public <P extends JpsElementProperties> void setProperties(JpsModuleSourceRootType<P> type, P properties) {
    myContainer.getChild(TYPED_DATA_ROLE).setProperties(properties);
  }

  @NotNull
  @Override
  public JpsModuleSourceRootType<?> getRootType() {
    return myContainer.getChild(TYPED_DATA_ROLE).getType();
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  @Override
  public JpsModuleSourceRootImpl createCopy() {
    return new JpsModuleSourceRootImpl(this);
  }
}
