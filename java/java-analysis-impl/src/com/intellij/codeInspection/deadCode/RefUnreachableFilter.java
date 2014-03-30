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
 * Time: 12:07:30 AM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.RefFilter;
import org.jetbrains.annotations.NotNull;

public class RefUnreachableFilter extends RefFilter {
  @NotNull
  protected GlobalInspectionTool myTool;
  @NotNull protected final GlobalInspectionContext myContext;

  public RefUnreachableFilter(@NotNull GlobalInspectionTool tool, @NotNull GlobalInspectionContext context) {
    myTool = tool;
    myContext = context;
  }

  @Override
  public int getElementProblemCount(@NotNull RefJavaElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    if (refElement.isSyntheticJSP()) return 0;
    if (!(refElement instanceof RefMethod || refElement instanceof RefClass || refElement instanceof RefField)) return 0;
    if (!((GlobalInspectionContextBase)myContext).isToCheckMember(refElement, myTool)) return 0;
    return ((RefElementImpl)refElement).isSuspicious() ? 1 : 0;
  }
}
