/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class DialogWrapperPeerFactory {
  @NotNull
  public static DialogWrapperPeerFactory getInstance() {
    if (ApplicationManager.getApplication() == null) {
      return getInstanceByName();
    }

    DialogWrapperPeerFactory factory = ServiceManager.getService(DialogWrapperPeerFactory.class);
    return factory == null ? getInstanceByName() : factory;
  }

  @NotNull
  private static DialogWrapperPeerFactory getInstanceByName() {
    try {
      return (DialogWrapperPeerFactory)Class.forName("com.intellij.openapi.ui.impl.DialogWrapperPeerFactoryImpl").newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException("Can't instantiate DialogWrapperPeerFactory", e);
    }
  }

  @NotNull
  public abstract DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, @Nullable Project project, boolean canBeParent);
  @NotNull
  public abstract DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, boolean canBeParent);

  @NotNull
  public abstract DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, @Nullable Project project, boolean canBeParent, DialogWrapper.IdeModalityType ideModalityType);

  @Deprecated
  @NotNull
  public abstract DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, @NotNull Component parent, boolean canBeParent);

  @NotNull
  public abstract DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, boolean canBeParent, DialogWrapper.IdeModalityType ideModalityType);
  @NotNull
  public abstract DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, Window owner, boolean canBeParent, DialogWrapper.IdeModalityType ideModalityType);
}