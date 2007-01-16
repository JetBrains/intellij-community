package com.intellij.mock;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

public class MockProject extends UserDataHolderBase implements ProjectEx {
  public void dispose() {
  }

  public boolean isSavePathsRelative() {
    return false;
  }

  public void setSavePathsRelative(boolean b) {
  }

  public boolean isDefault() {
    return false;
  }

  public PomModel getModel() {
    return null;
  }

  public boolean isDummy() {
    return false;
  }

  public boolean isDisposed() {
    return false;
  }

  @NotNull
  public ComponentConfig[] getComponentConfigurations() {
    throw new UnsupportedOperationException("Method getComponentConfigurations is not supported in " + getClass());
  }

  @Nullable
  public Object getComponent(final ComponentConfig componentConfig) {
    throw new UnsupportedOperationException("Method getComponent is not supported in " + getClass());
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

  public String getName() {
    return null;
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

  public String getProjectFilePath() {
    return null;
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

  public BaseComponent getComponent(String name) {
    return null;
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    return null;
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    return null;
  }

  @NotNull
  public Class[] getComponentInterfaces() {
    return ArrayUtil.EMPTY_CLASS_ARRAY;
  }

  public boolean hasComponent(Class interfaceClass) {
    return false;
  }


  public MessageBus getMessageBus() {
    return null;
  }

  @NotNull
  public <T> T[] getComponents(Class<T> baseClass) {
    return (T[]) ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }

  public GlobalSearchScope getAllScope() {
    return null;
  }

  public GlobalSearchScope getProjectScope() {
    return null;
  }
}
