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
  enum Event {
    START_STATIC,
    CHANGE_LEVEL,
    SET_DECLARATION_HOLDER,
    SET_CURRENT_FILE_CONTEXT,
    CHANGE_PROPERTY_PREFIX,
    SET_PARAMETERS
  }

  boolean execute(PsiElement element, PsiSubstitutor substitutor);
  <T> T getHint(Class<T> hintClass);
  void handleEvent(Event event, Object associated);
}
