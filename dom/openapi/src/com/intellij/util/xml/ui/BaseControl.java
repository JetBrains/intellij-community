/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public abstract class BaseControl<Bound extends JComponent, T> implements DomUIControl {
  public static final Color ERROR_BACKGROUND = new Color(255,204,204);
  public static final Color ERROR_FOREGROUND = SimpleTextAttributes.ERROR_ATTRIBUTES.getFgColor();
  public static final Color WARNING_BACKGROUND = new Color(255,255,204);

  private final EventDispatcher<CommitListener> myDispatcher = EventDispatcher.create(CommitListener.class);

  private Bound myBoundComponent;
  private DomWrapper<T> myDomWrapper;
  private boolean myCommitting;

  private Color myDefaultForeground;
  private Color myDefaultBackground;

  protected BaseControl(final DomWrapper<T> domWrapper) {
    myDomWrapper = domWrapper;
  }

  private void checkInitialized() {
    if (myBoundComponent != null) return;

    initialize(null);
  }

  protected JComponent getHighlightedComponent(Bound component) {
    return component;
  }

  protected final Color getDefaultBackground() {
    return myDefaultBackground;
  }

  protected final Color getDefaultForeground() {
    return myDefaultForeground;
  }

  protected final Color getErrorBackground() {
    return ERROR_BACKGROUND;
  }

  protected final Color getWarningBackground() {
    return WARNING_BACKGROUND;
  }

  protected final Color getErrorForeground() {
    return ERROR_FOREGROUND;
  }


  private void initialize(final Bound boundComponent) {
    myBoundComponent = createMainComponent(boundComponent);
    final JComponent highlightedComponent = getHighlightedComponent(myBoundComponent);
    myDefaultForeground = highlightedComponent.getForeground();
    myDefaultBackground = highlightedComponent.getBackground();
    final JComponent component = getComponentToListenFocusLost(myBoundComponent);
    if (component != null) {
      component.addFocusListener(new FocusListener() {
        public void focusGained(FocusEvent e) {
        }

        public void focusLost(FocusEvent e) {
          if (!e.isTemporary() && myDomWrapper.isValid()) {
            commit();
          }
        }
      });
    }

    updateComponent();
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

  public final DomElement getDomElement() {
    return myDomWrapper.getDomElement();
  }

  public final DomWrapper<T> getDomWrapper() {
    return myDomWrapper;
  }

  public final Bound getComponent() {
    checkInitialized();
    return myBoundComponent;
  }

  public void dispose() {
  }

  public final void commit() {
    if (myDomWrapper.isValid() && !isCommitted()) {
      setValueToXml(getValue());
      updateComponent();
    }
  }

  private static boolean valuesAreEqual(final Object valueInXml, final Object valueInControl) {
    return "".equals(valueInControl) && null == valueInXml ||
           equalModuloTrim(valueInXml, valueInControl) ||
           Comparing.equal(valueInXml, valueInControl);
  }

  private static boolean equalModuloTrim(final Object valueInXml, final Object valueInControl) {
    return valueInXml instanceof String && valueInControl instanceof String && ((String)valueInXml).trim().equals(((String)valueInControl).trim());
  }

  public final void reset() {
    if (!myCommitting) {
      doReset();
      updateComponent();
    }
  }

  protected void updateComponent() {
  }

  protected void doReset() {
    if (!isCommitted()) {
      setValue(getValueFromXml());
    }
  }

  protected final boolean isCommitted() {
    return valuesAreEqual(getValueFromXml(), getValue());
  }

  private void setValueToXml(final T value) {
    if (myCommitting) return;
    myCommitting = true;
    try {
      new WriteCommandAction(getProject(), getDomWrapper().getFile()) {
        protected void run(Result result) throws Throwable {
          final CommitListener multicaster = myDispatcher.getMulticaster();
          multicaster.beforeCommit(BaseControl.this);
          myDomWrapper.setValue("".equals(value) ? null : value);
          multicaster.afterCommit(BaseControl.this);
        }
      }.execute();
    }
    finally {
      myCommitting = false;
    }
  }

  protected final Project getProject() {
    return myDomWrapper.getProject();
  }

  private T getValueFromXml() {
    try {
      return myDomWrapper.getValue();
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }


  public boolean canNavigate(DomElement element) {
    return false;
  }

  public void navigate(DomElement element) {
  }

  protected abstract T getValue();
  protected abstract void setValue(T value);

}
