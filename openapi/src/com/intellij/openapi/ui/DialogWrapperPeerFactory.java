/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;

import java.awt.*;

public abstract class DialogWrapperPeerFactory {
  public abstract DialogWrapperPeer createPeer(DialogWrapper wrapper, Project project, boolean canBeParent);
  public abstract DialogWrapperPeer createPeer(DialogWrapper wrapper, boolean canBeParent);
  public abstract DialogWrapperPeer createPeer(DialogWrapper wrapper, Component parent, boolean canBeParent);
}