/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.project.DumbAware;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.DomIconProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author peter
 */
public class DefaultDomTargetIconProvider extends DomIconProvider implements DumbAware {
  public Icon getIcon(@NotNull DomElement element, int flags) {
    return ElementPresentationManager.getIcon(element);
  }
}
