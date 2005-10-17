package com.intellij.mock;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.util.ArrayUtil;

import java.util.Map;

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

  public String getProjectFilePath() {
    return null;
  }

  public VirtualFile getWorkspaceFile() {
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

  public Class[] getComponentInterfaces() {
    return new Class[0];
  }

  public boolean hasComponent(Class interfaceClass) {
    return false;
  }

  public <T> T[] getComponents(Class<T> baseInterfaceClass) {
    return ArrayUtil.<T>emptyArray();
  }

  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }
}
