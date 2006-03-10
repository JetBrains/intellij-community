/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;

import javax.swing.*;

/**
 * User: Sergey.Vasiliev
 * Date: Mar 1, 2006
 */
public abstract class DefaultAddAction<T extends DomElement> extends AnAction {

  public DefaultAddAction() {
  }

  public DefaultAddAction(String text) {
    super(text);
  }

  public DefaultAddAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }


  protected Class<? extends T> getElementClass() {
    return (Class<? extends T>)DomUtil.getRawType(getDomCollectionChildDescription().getType());
  }

  protected T doAdd() {
    return (T)getDomCollectionChildDescription().addValue(getParentDomElement(), getElementClass());
  }

  protected abstract DomCollectionChildDescription getDomCollectionChildDescription();

  protected abstract DomElement getParentDomElement();

  protected boolean beforeAddition() {
    return true;
  }

  protected void afterAddition(final AnActionEvent e, DomElement newElement) {
  }

  public void actionPerformed(final AnActionEvent e) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (beforeAddition()) {
          final T newElement = new WriteCommandAction<T>(getParentDomElement().getManager().getProject()) {
            protected void run(Result<T> result) throws Throwable {
              result.setResult(doAdd());
            }
          }.execute().getResultObject();
          afterAddition(e, newElement);
        }
      }
    });
  }
}