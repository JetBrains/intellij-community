/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Nov 25, 2002
 * Time: 3:13:59 PM
 * To change this template use Options | File Templates.
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.scope;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;

public interface PsiScopeProcessor {
  public static final class Event{
    public static final Event START_STATIC = new Event();
    public static final Event BEGIN_GROUP = new Event();
    public static final Event END_GROUP = new Event();
    public static final Event CHANGE_LEVEL = new Event();
    public static final Event SET_DECLARATION_HOLDER = new Event();
    public static final Event SET_CURRENT_FILE_CONTEXT = new Event();
    public static final Event CHANGE_PROPERTY_PREFIX = new Event();
    public static final Event SET_PARAMETERS = new Event();
  }

  boolean execute(PsiElement element, PsiSubstitutor substitutor);
  <T> T getHint(Class<T> hintClass);
  void handleEvent(Event event, Object associated);
}
