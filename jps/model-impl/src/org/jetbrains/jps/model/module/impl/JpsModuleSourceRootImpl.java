package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementType;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import java.io.File;

/**
 * @author nik
 */
public class JpsModuleSourceRootImpl<P extends JpsElement> extends JpsCompositeElementBase<JpsModuleSourceRootImpl<P>> implements JpsTypedModuleSourceRoot<P> {
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

  @Nullable
  @Override
  public <P extends JpsElement> JpsTypedModuleSourceRoot<P> asTyped(@NotNull JpsModuleSourceRootType<P> type) {
    //noinspection unchecked
    return myRootType.equals(type) ? (JpsTypedModuleSourceRoot<P>)this : null;
  }

  @Override
  public JpsElementType<?> getType() {
    return myRootType;
  }

  @NotNull
  @Override
  public P getProperties() {
    return myContainer.getChild(myRootType.getPropertiesRole());
  }

  @NotNull
  @Override
  public JpsModuleSourceRootType<P> getRootType() {
    return myRootType;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  @Override
  public File getFile() {
    return JpsPathUtil.urlToFile(myUrl);
  }

  @NotNull
  @Override
  public JpsModuleSourceRootImpl<P> createCopy() {
    return new JpsModuleSourceRootImpl<P>(this);
  }
}
