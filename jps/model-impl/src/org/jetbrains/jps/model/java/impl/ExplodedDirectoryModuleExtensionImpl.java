package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.impl.JpsElementBase;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.ExplodedDirectoryModuleExtension;

/**
 * @author nik
 */
public class ExplodedDirectoryModuleExtensionImpl extends JpsElementBase<ExplodedDirectoryModuleExtensionImpl> implements
                                                                                                               ExplodedDirectoryModuleExtension {
  private String myExplodedUrl;
  private boolean myExcludeExploded;

  public ExplodedDirectoryModuleExtensionImpl() {
  }

  public ExplodedDirectoryModuleExtensionImpl(ExplodedDirectoryModuleExtensionImpl original) {
    myExcludeExploded = original.myExcludeExploded;
    myExplodedUrl = original.myExplodedUrl;
  }

  @Override
  public String getExplodedUrl() {
    return myExplodedUrl;
  }

  @Override
  public void setExplodedUrl(String explodedUrl) {
    if (!Comparing.equal(myExplodedUrl, explodedUrl)) {
      myExplodedUrl = explodedUrl;
      fireElementChanged();
    }
  }

  @Override
  public boolean isExcludeExploded() {
    return myExcludeExploded;
  }

  @Override
  public void setExcludeExploded(boolean excludeExploded) {
    if (myExcludeExploded != excludeExploded) {
      myExcludeExploded = excludeExploded;
      fireElementChanged();
    }
  }

  @NotNull
  @Override
  public ExplodedDirectoryModuleExtensionImpl createCopy() {
    return new ExplodedDirectoryModuleExtensionImpl(this);
  }

  @Override
  public void applyChanges(@NotNull ExplodedDirectoryModuleExtensionImpl modified) {
    setExcludeExploded(modified.myExcludeExploded);
    setExplodedUrl(modified.myExplodedUrl);
  }

  public static class ExplodedDirectoryModuleExtensionRole extends JpsElementChildRoleBase<ExplodedDirectoryModuleExtension> implements JpsElementCreator<ExplodedDirectoryModuleExtension> {
    public static final ExplodedDirectoryModuleExtensionRole INSTANCE = new ExplodedDirectoryModuleExtensionRole();

    public ExplodedDirectoryModuleExtensionRole() {
      super("exploded directory");
    }

    @NotNull
    @Override
    public ExplodedDirectoryModuleExtension create() {
      return new ExplodedDirectoryModuleExtensionImpl();
    }
  }
}
