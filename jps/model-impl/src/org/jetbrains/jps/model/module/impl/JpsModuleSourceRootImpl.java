package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * @author nik
 */
public class JpsModuleSourceRootImpl<P extends JpsElement> extends JpsCompositeElementBase<JpsModuleSourceRootImpl<P>> implements JpsModuleSourceRoot {
  private final JpsModuleSourceRootType<P> myRootType;
  private final String myUrl;

  public JpsModuleSourceRootImpl(String url, JpsModuleSourceRootType<P> type, P properties) {
    super();
    myRootType = type;
    myContainer.setChild(type.getPropertiesRole(), properties);
    myUrl = url;
  }

  private JpsModuleSourceRootImpl(JpsModuleSourceRootImpl<P> original) {
    super(original);
    myRootType = original.myRootType;
    myUrl = original.myUrl;
  }

  @Override
  public <P extends JpsElement> P getProperties(@NotNull JpsModuleSourceRootType<P> type) {
    if (myRootType.equals(type)) {
      //noinspection unchecked
      return (P)myContainer.getChild(myRootType.getPropertiesRole());
    }
    return null;
  }

  @NotNull
  @Override
  public P getProperties() {
    return myContainer.getChild(myRootType.getPropertiesRole());
  }

  @NotNull
  @Override
  public JpsModuleSourceRootType<?> getRootType() {
    return myRootType;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  @Override
  public JpsModuleSourceRootImpl<P> createCopy() {
    return new JpsModuleSourceRootImpl<P>(this);
  }
}
