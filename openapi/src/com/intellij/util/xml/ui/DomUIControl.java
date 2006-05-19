/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;

import javax.swing.*;

/**
 * @author peter
 * 
 * @see DomUIFactory
 */
public interface DomUIControl extends CommittablePanel {

  DomElement getDomElement();

  void bind(JComponent component);

  void addCommitListener(CommitListener listener);

  void removeCommitListener(CommitListener listener);

  boolean canNavigate(DomElement element);

  void navigate(DomElement element);
}
