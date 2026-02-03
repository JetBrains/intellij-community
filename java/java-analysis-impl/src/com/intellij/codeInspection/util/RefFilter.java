/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInspection.util;

import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefJavaElement;
import com.intellij.codeInspection.reference.RefParameter;
import org.jetbrains.annotations.NotNull;

public abstract class RefFilter {
  // Default accepts implementation accepts element if one under unaccepted one. Thus it will accept all and only upper level classes.
  public int getElementProblemCount(@NotNull RefJavaElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    RefEntity refOwner = refElement.getOwner();
    if (!(refOwner instanceof RefJavaElement)) return 1;

    return 1 - getElementProblemCount((RefJavaElement)refOwner);
  }

  public final boolean accepts(@NotNull RefJavaElement refElement) {
    return getElementProblemCount(refElement) > 0;
  }
}
