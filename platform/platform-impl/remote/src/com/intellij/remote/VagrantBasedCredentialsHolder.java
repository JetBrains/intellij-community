// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VagrantBasedCredentialsHolder {
  private static final @NonNls String VAGRANT_FOLDER = "VAGRANT_FOLDER";
  private static final @NonNls String MACHINE_NAME = "MACHINE_NAME";

  private @NotNull String myVagrantFolder;
  private String myMachineName;

  public VagrantBasedCredentialsHolder() {
    myVagrantFolder = "";
  }

  public VagrantBasedCredentialsHolder(@NotNull String vagrantFolder, @Nullable String machineName) {
    myVagrantFolder = vagrantFolder;
    myMachineName = machineName;
  }

  public void setVagrantFolder(@NotNull String vagrantFolder) {
    myVagrantFolder = vagrantFolder;
  }

  public @NotNull String getVagrantFolder() {
    return myVagrantFolder;
  }

  public @Nullable String getMachineName() {
    return myMachineName;
  }

  public void setMachineName(String machineName) {
    myMachineName = machineName;
  }

  public void load(Element element) {
    final String folder = StringUtil.notNullize(element.getAttributeValue(VAGRANT_FOLDER));
    setVagrantFolder(PathUtil.toSystemDependentName(folder));
    setMachineName(element.getAttributeValue(MACHINE_NAME));
  }

  public void save(Element element) {
    element.setAttribute(VAGRANT_FOLDER, PathUtil.toSystemIndependentName(getVagrantFolder()));
    if (StringUtil.isNotEmpty(getMachineName())) {
      element.setAttribute(MACHINE_NAME, getMachineName());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VagrantBasedCredentialsHolder holder = (VagrantBasedCredentialsHolder)o;

    if (!myVagrantFolder.equals(holder.myVagrantFolder)) return false;

    if (StringUtil.isNotEmpty(myMachineName) || StringUtil.isNotEmpty(holder.myMachineName)) {
      return StringUtil.equals(myMachineName, holder.myMachineName);
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myVagrantFolder.hashCode();
    result = 31 * result + (myMachineName != null ? myMachineName.hashCode() : 0);
    return result;
  }

  @Override
  public @NonNls String toString() {
    return "VagrantBasedCredentialsHolder{" +
           "myVagrantFolder='" + myVagrantFolder + '\'' +
           ", myMachineName='" + myMachineName + '\'' +
           '}';
  }
}
