/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;

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
    return (Class<? extends T>)DomReflectionUtil.getRawType(getDomCollectionChildDescription().getType());
  }

  protected void tuneNewValue(T t) {
  }

  protected abstract DomCollectionChildDescription getDomCollectionChildDescription();

  protected abstract DomElement getParentDomElement();

  protected boolean beforeAddition() {
    return true;
  }

  protected void afterAddition(@NotNull T newElement) {
  }

  public void actionPerformed(final AnActionEvent e) {
    final Runnable runnable = new Runnable() {
      public void run() {
        if (beforeAddition()) {
          final T result = performElementAddition();
          if (result != null) {
            afterAddition(result);
          }
        }
      }
    };
    runnable.run();
    //ApplicationManager.getApplication().invokeLater(runnable);
  }

  protected T performElementAddition() {
    final DomElement parent = getParentDomElement();
    final DomManager domManager = parent.getManager();
    final ClassChooser[] oldChooser = new ClassChooser[]{null};
    final Class[] aClass = new Class[]{null};
    final StableElement<T> result = new WriteCommandAction<StableElement<T>>(domManager.getProject(), parent.getRoot().getFile()) {
      protected void run(Result<StableElement<T>> result) throws Throwable {
        final T t = (T)getDomCollectionChildDescription().addValue(getParentDomElement(), getElementClass());
        tuneNewValue(t);
        aClass[0] = DomReflectionUtil.getRawType(parent.getGenericInfo().getCollectionChildDescription(t.getXmlElementName()).getType());
        oldChooser[0] = ClassChooserManager.getClassChooser(aClass[0]);
        final SmartPsiElementPointer pointer =
          SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(t.getXmlTag());
        ClassChooserManager.registerClassChooser(aClass[0], new ClassChooser() {
          public Class<? extends T> chooseClass(final XmlTag tag) {
            if (tag == pointer.getElement()) {
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
        result.setResult((StableElement<T>)t.createStableCopy());
      }
    }.execute().getResultObject();
    if (result != null) {
      ClassChooserManager.registerClassChooser(aClass[0], oldChooser[0]);
    }
    return result.getWrappedElement();
  }
}