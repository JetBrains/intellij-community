// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.Constructor;

public abstract class DialogWrapperPeerFactory {
  public static @NotNull DialogWrapperPeerFactory getInstance() {
    if (ApplicationManager.getApplication() == null) {
      return getInstanceByName();
    }

    DialogWrapperPeerFactory factory = ApplicationManager.getApplication().getService(DialogWrapperPeerFactory.class);
    return factory == null ? getInstanceByName() : factory;
  }

  private static @NotNull DialogWrapperPeerFactory getInstanceByName() {
    try {
      Constructor<?> constructor = Class.forName("com.intellij.openapi.ui.impl.DialogWrapperPeerFactoryImpl").getDeclaredConstructor();
      constructor.setAccessible(true);
      return (DialogWrapperPeerFactory)constructor.newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException("Can't instantiate DialogWrapperPeerFactory", e);
    }
  }

  public abstract @NotNull DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, @Nullable Project project, boolean canBeParent);

  public abstract @NotNull DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, boolean canBeParent);

  public abstract @NotNull DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper,
                                                        @Nullable Project project,
                                                        boolean canBeParent,
                                                        DialogWrapper.IdeModalityType ideModalityType);

  public abstract @NotNull DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, @NotNull Component parent, boolean canBeParent);

  public abstract @NotNull DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper,
                                                        boolean canBeParent,
                                                        DialogWrapper.IdeModalityType ideModalityType);

  public abstract @NotNull DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper,
                                                        Window owner,
                                                        boolean canBeParent,
                                                        DialogWrapper.IdeModalityType ideModalityType);
}