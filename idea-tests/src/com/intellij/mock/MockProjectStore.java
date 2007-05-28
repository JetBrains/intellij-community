/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.mock;

import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author peter
 */
public class MockProjectStore implements IProjectStore {
  public boolean checkVersion() {
    throw new UnsupportedOperationException("Method checkVersion is not yet implemented in " + getClass().getName());
  }

  @SuppressWarnings({"EmptyMethod"})
  public boolean isSavePathsRelative() {
    throw new UnsupportedOperationException("Method isSavePathsRelative is not yet implemented in " + getClass().getName());
  }

  public void setProjectFilePath(final String filePath) {
    throw new UnsupportedOperationException("Method setProjectFilePath is not yet implemented in " + getClass().getName());
  }

  public void setSavePathsRelative(final boolean b) {
    throw new UnsupportedOperationException("Method setSavePathsRelative is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public VirtualFile getProjectBaseDir() {
    throw new UnsupportedOperationException("Method getProjectBaseDir is not yet implemented in " + getClass().getName());
  }//------ This methods should be got rid of

  public void setStorageFormat(final StorageFormat storageFormat) {
    throw new UnsupportedOperationException("Method setStorageFormat not implemented in " + getClass());
  }

  public String getLocation() {
    throw new UnsupportedOperationException("Method getLocation not implemented in " + getClass());
  }

  @NotNull
  public String getProjectName() {
    throw new UnsupportedOperationException("Method getProjectName not implemented in " + getClass());
  }

  public void loadProject() throws IOException, JDOMException, InvalidDataException {
    throw new UnsupportedOperationException("Method loadProject is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public VirtualFile getProjectFile() {
    throw new UnsupportedOperationException("Method getProjectFile is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public VirtualFile getWorkspaceFile() {
    throw new UnsupportedOperationException("Method getWorkspaceFile is not yet implemented in " + getClass().getName());
  }

  public void loadProjectFromTemplate(ProjectImpl project) {
    throw new UnsupportedOperationException("Method loadProjectFromTemplate is not yet implemented in " + getClass().getName());
  }

  @NotNull
  public String getProjectFileName() {
    throw new UnsupportedOperationException("Method getProjectFileName is not yet implemented in " + getClass().getName());
  }

  @NotNull
  public String getProjectFilePath() {
    return null;
  }

  public Set<String> getMacroTrackingSet() {
    return new TreeSet<String>();
  }

  public void initStore() {
    throw new UnsupportedOperationException("Method initStore is not yet implemented in " + getClass().getName());
  }

  public void initComponent(Object component) {
    throw new UnsupportedOperationException("Method initComponent is not yet implemented in " + getClass().getName());
  }

  public void commit() {
    throw new UnsupportedOperationException("Method commit is not yet implemented in " + getClass().getName());
  }

  public boolean save() throws IOException {
    throw new UnsupportedOperationException("Method save is not yet implemented in " + getClass().getName());
  }

  public void load() throws IOException {
    throw new UnsupportedOperationException("Method load is not yet implemented in " + getClass().getName());
  }

  public Collection<String> getUsedMacros() {
    throw new UnsupportedOperationException("Method getUsedMacros not implemented in " + getClass());
  }

  public List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures) {
    throw new UnsupportedOperationException("Method getAllStorageFiles is not yet implemented in " + getClass().getName());
  }

  public SaveSession startSave() throws IOException {
    throw new UnsupportedOperationException("Method startSave not implemented in " + getClass());
  }

  public List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures) {
    throw new UnsupportedOperationException("Method getAllStorageFilesToSave is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public String getPresentableUrl() {
    throw new UnsupportedOperationException("Method getPresentableUrl not implemented in " + getClass());
  }
}
