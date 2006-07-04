/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Type;

/**
 * User: Sergey.Vasiliev
 * Date: Mar 1, 2006
 */
public abstract class DefaultAddAction<T extends DomElement> extends AnAction {

  public DefaultAddAction() {
    super(ApplicationBundle.message("action.add"));
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

  protected void afterAddition(@NotNull T newElement) {
  }

  public final void actionPerformed(final AnActionEvent e) {
    final T result = performElementAddition();
    if (result != null) {
      afterAddition(result);
    }
  }

  @Nullable
  protected T performElementAddition() {
    final DomElement parent = getParentDomElement();
    final DomManager domManager = parent.getManager();
    final TypeChooser[] oldChoosers = new TypeChooser[]{null};
    final Type[] aClass = new Type[]{null};
    final StableElement<T> result = new WriteCommandAction<StableElement<T>>(domManager.getProject(), parent.getRoot().getFile()) {
      protected void run(Result<StableElement<T>> result) throws Throwable {
        final T t = (T)getDomCollectionChildDescription().addValue(getParentDomElement(), getElementClass());
        tuneNewValue(t);
        aClass[0] = parent.getGenericInfo().getCollectionChildDescription(t.getXmlElementName()).getType();
        oldChoosers[0] = TypeChooserManager.getClassChooser(aClass[0]);
        final SmartPsiElementPointer pointer =
          SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(t.getXmlTag());
        TypeChooserManager.registerClassChooser(aClass[0], new TypeChooser() {
          public Type chooseType(final XmlTag tag) {
            if (tag == pointer.getElement()) {
              return getElementClass();
            }
            return oldChoosers[0].chooseType(tag);
          }

          public void distinguishTag(final XmlTag tag, final Type aClass) throws IncorrectOperationException {
            oldChoosers[0].distinguishTag(tag, aClass);
          }

          public Type[] getChooserTypes() {
            return oldChoosers[0].getChooserTypes();
          }
        });
        result.setResult((StableElement<T>)t.createStableCopy());
      }
    }.execute().getResultObject();
    if (result != null) {
      TypeChooserManager.registerClassChooser(aClass[0], oldChoosers[0]);
    }
    return result.getWrappedElement();
  }
}