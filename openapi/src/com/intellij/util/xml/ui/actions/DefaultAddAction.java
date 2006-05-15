/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.xml.XmlTag;

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
          final DomElement parent = getParentDomElement();
          final int[] index = new int[]{-1};
          final DomCollectionChildDescription[] description = new DomCollectionChildDescription[]{null};
          final DomManager domManager = parent.getManager();
          final ClassChooser[] oldChooser = new ClassChooser[]{null};
          final Class[] aClass = new Class[]{null};
          final String[] name = new String[]{null};
          new WriteCommandAction(domManager.getProject()) {
            protected void run(Result result) throws Throwable {
              final T t = doAdd();
              name[0] = t.getXmlElementName();
              description[0] = parent.getGenericInfo().getCollectionChildDescription(name[0]);
              index[0] = description[0].getValues(parent).indexOf(t);
              aClass[0] = DomUtil.getRawType(description[0].getType());
              oldChooser[0] = ClassChooserManager.getClassChooser(aClass[0]);
              ClassChooserManager.registerClassChooser(aClass[0], new ClassChooser() {
                public Class<? extends T> chooseClass(final XmlTag tag) {
                  if (tag == getParentDomElement().getXmlTag().findSubTags(name[0])[index[0]]) {
                    return getElementClass();
                  }
                  return oldChooser[0].chooseClass(tag);
                }

                public void distinguishTag(final XmlTag tag, final Class aClass) throws IncorrectOperationException {
                  oldChooser[0].distinguishTag(tag, aClass);
                }

                public Class[] getChooserClasses() {
                  return oldChooser[0].getChooserClasses();
                }
              });
            }
          }.execute();
          ClassChooserManager.registerClassChooser(aClass[0], oldChooser[0]);
          afterAddition(e, description[0].getValues(getParentDomElement()).get(index[0]));
        }
      }
    });
  }
}