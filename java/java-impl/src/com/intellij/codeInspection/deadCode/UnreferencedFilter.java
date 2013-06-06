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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 2, 2001
 * Time: 12:14:37 AM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.*;
import org.jetbrains.annotations.NotNull;

public class UnreferencedFilter extends RefUnreachableFilter {
  public UnreferencedFilter(@NotNull InspectionTool tool) {
    super(tool);
  }

  @Override
  public int getElementProblemCount(RefJavaElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    if (refElement.isEntry() || !((RefElementImpl)refElement).isSuspicious() || refElement.isSyntheticJSP()) return 0;

    if (!(refElement instanceof RefMethod || refElement instanceof RefClass || refElement instanceof RefField)) return 0;
    if (!myTool.getContext().isToCheckMember(refElement, myTool)) return 0;

    if (refElement instanceof RefField) {
      RefField refField = (RefField) refElement;
      if (refField.isUsedForReading() && !refField.isUsedForWriting()) return 1;
      if (refField.isUsedForWriting() && !refField.isUsedForReading()) return 1;
    }

    if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) return 0;
    return -1;
  }
}
