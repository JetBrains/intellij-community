package com.intellij.mock;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.PomModel;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockProject extends MockComponentManager implements ProjectEx {
  public MockProject() {
    super(ApplicationManager.getApplication().getPicoContainer());
  }

  public boolean isSavePathsRelative() {
    return false;
  }

  public void setSavePathsRelative(boolean b) {
  }

  public boolean isDefault() {
    return false;
  }

  @NotNull
  public PomModel getModel() {
    return ServiceManager.getService(this, PomModel.class);
  }

  public boolean isDummy() {
    return false;
  }

  public Condition getDisposed() {
    return new Condition() {
      public boolean value(final Object o) {
        return isDisposed();
      }
    };
  }

  @NotNull
  public IProjectStore getStateStore() {
    return new MockProjectStore();
  }

  public void init() {
  }

  public boolean isOptimiseTestLoadSpeed() {
    return false;
  }

  public void setOptimiseTestLoadSpeed(final boolean optimiseTestLoadSpeed) {
    throw new UnsupportedOperationException("Method setOptimiseTestLoadSpeed not implemented in " + getClass());
  }

  public boolean isOpen() {
    return false;
  }

  public boolean isInitialized() {
    return false;
  }

  public ReplacePathToMacroMap getMacroReplacements() {
    return null;
  }

  public ExpandMacroToPathMap getExpandMacroReplacements() {
    return null;
  }

  public VirtualFile getProjectFile() {
    return null;
  }

  @NotNull
  public String getName() {
    return "";
  }

  @Nullable
  @NonNls
  public String getPresentableUrl() {
    return null;
  }

  @NotNull
  @NonNls
  public String getLocationHash() {
    return "mock";
  }

  @NotNull
  @NonNls
  public String getLocation() {
    throw new UnsupportedOperationException("Method getLocation not implemented in " + getClass());
  }

  @NotNull
  public String getProjectFilePath() {
    return "";
  }

  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Nullable
  public VirtualFile getBaseDir() {
    return null;
  }

  public void save() {
  }

  public GlobalSearchScope getAllScope() {
    return new GlobalSearchScope() {
      public boolean contains(final VirtualFile file) {
        return false;
      }

      public int compare(final VirtualFile file1, final VirtualFile file2) {
        return 0;
      }

      public boolean isSearchInModuleContent(@NotNull final Module aModule) {
        return false;
      }

      public boolean isSearchInLibraries() {
        return false;
      }
    };
  }

  public GlobalSearchScope getProjectScope() {
    return getAllScope();
  }

}
