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
  private static final JpsTypedDataKind<JpsModuleSourceRootType<?>> TYPED_DATA_KIND = new JpsTypedDataKind<JpsModuleSourceRootType<?>>();
  private String myUrl;

  public <P extends JpsElementProperties> JpsModuleSourceRootImpl(String url, JpsModuleSourceRootType<P> type, P properties) {
    super();
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsModuleSourceRootType<?>>(type, properties));
    myUrl = url;
  }

  private JpsModuleSourceRootImpl(JpsModuleSourceRootImpl original) {
    super(original);
    myUrl = original.myUrl;
  }

  @Override
  public <P extends JpsElementProperties> P getProperties(@NotNull JpsModuleSourceRootType<P> type) {
    return myContainer.getChild(TYPED_DATA_KIND).getProperties(type);
  }

  @NotNull
  @Override
  public JpsElementProperties getProperties() {
    return myContainer.getChild(TYPED_DATA_KIND).getProperties();
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
  public JpsModuleSourceRootImpl createCopy() {
    return new JpsModuleSourceRootImpl(this);
  }
}
