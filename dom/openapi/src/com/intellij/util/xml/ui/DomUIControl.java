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
public abstract class DomUIControl<T extends DomElement> implements CommittablePanel {

  public abstract T getDomElement();

  public abstract void bind(JComponent component);

  public abstract void addCommitListener(CommitListener listener);

  public abstract void removeCommitListener(CommitListener listener);

  public abstract boolean canNavigate(DomElement element);

  public abstract void navigate(DomElement element);
}
