/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Nov 25, 2002
 * Time: 3:13:59 PM
 * To change this template use Options | File Templates.
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.scope;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;

public interface PsiScopeProcessor {
  final class Event{
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
