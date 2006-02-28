/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.javaee.ui.Warning;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ui.DomUIControl;
import com.intellij.util.xml.ui.CommitListener;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public abstract class BaseControl<Bound extends JComponent, T> implements DomUIControl {
  private final EventDispatcher<CommitListener> myDispatcher = EventDispatcher.create(CommitListener.class);

  private Bound myBoundComponent;
  private com.intellij.util.xml.ui.DomWrapper<T> myDomWrapper;
  private boolean myCommitting;

  protected BaseControl(final com.intellij.util.xml.ui.DomWrapper<T> domWrapper) {
    myDomWrapper = domWrapper;
  }

  private void checkInitialized() {
    if (myBoundComponent != null) return;

    initialize(null);
  }

  private void initialize(final Bound boundComponent) {
    myBoundComponent = createMainComponent(boundComponent);
    final JComponent component = getComponentToListenFocusLost(myBoundComponent);
    if (component != null) {
      component.addFocusListener(new FocusListener() {
        public void focusGained(FocusEvent e) {
        }

        public void focusLost(FocusEvent e) {
          if (!e.isTemporary() && myDomWrapper.getDomElement().isValid()) {
            commit();
          }
        }
      });
    }
  }

  @Nullable
  protected JComponent getComponentToListenFocusLost(Bound component) {
    return null;
  }

  protected abstract Bound createMainComponent(Bound boundedComponent);

  public void bind(JComponent component) {
    initialize((Bound)component);
  }

  public void addCommitListener(CommitListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeCommitListener(CommitListener listener) {
    myDispatcher.removeListener(listener);
  }

  public JComponent getFocusedComponent() {
    checkInitialized();
    return myBoundComponent;
  }

  public final Bound getBoundComponent() {
    checkInitialized();
    return myBoundComponent;
  }

  public final DomElement getDomElement() {
    return myDomWrapper.getDomElement();
  }

  public final Bound getComponent() {
    checkInitialized();
    return myBoundComponent;
  }

  public void dispose() {
  }

  public final void commit() {
    assert getDomElement().isValid();
    final T valueInControl = getValue(getBoundComponent());
    if (!valuesAreEqual(getValueFromXml(), valueInControl)) {
      setValueToXml(valueInControl);
    }
  }

  protected boolean valuesAreEqual(final T valueInXml, final T valueInControl) {
    if ("".equals(valueInControl) && null == valueInXml) return true;

    return Comparing.equal(valueInXml, valueInControl);
  }

  public final void reset() {
    if (!myCommitting) {
      final T t = getValueFromXml();
      if (!valuesAreEqual(t, getValue(getBoundComponent()))) {
        setValue(getBoundComponent(), t);
      }
    }
  }

  public final List<Warning> getWarnings() {
    return Collections.emptyList();
  }

  private void setValueToXml(final T value) {
    if (myCommitting) return;
    myCommitting = true;
    try {
      new WriteCommandAction(getProject()) {
        protected void run(Result result) throws Throwable {
          final CommitListener multicaster = myDispatcher.getMulticaster();
          multicaster.beforeCommit(BaseControl.this);
          myDomWrapper.setValue(value);
          multicaster.afterCommit(BaseControl.this);
        }
      }.execute();
    }
    finally {
      myCommitting = false;
    }
  }

  protected final Project getProject() {
    return getDomElement().getManager().getProject();
  }

  private T getValueFromXml() {
    try {
      return myDomWrapper.getValue();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected abstract T getValue(Bound component);
  protected abstract void setValue(Bound component, T value);

}
