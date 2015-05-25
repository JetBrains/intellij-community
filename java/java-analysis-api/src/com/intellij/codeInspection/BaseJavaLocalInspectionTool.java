/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this abstract class in order to provide new inspection tool functionality. The major API limitation here is
 * subclasses should be stateless. Thus <code>check&lt;XXX&gt;</code> methods will be called in no particular order and
 * instances of this class provided by {@link InspectionToolProvider#getInspectionClasses()} will be created on demand.
 * The other important thing is problem anchors (PsiElements) reported by <code>check&lt;XXX&gt;</code> methods should
 * lie under corresponding first parameter of one method.
 *
 * @see GlobalInspectionTool
 *
 * Please note that if your inspection/fixes/suppressions don't need UI components (e.g. Editor) to run, consider using
 * {@link BaseJavaBatchLocalInspectionTool} instead.
 */
public abstract class BaseJavaLocalInspectionTool extends AbstractBaseJavaLocalInspectionTool implements CustomSuppressableInspectionTool {
  @NotNull
  @Override
  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    String shortName = getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null) {
      throw new AssertionError("HighlightDisplayKey.find(" + shortName + ") is null. Inspection: "+getClass());
    }
    return SuppressManager.getInstance().createSuppressActions(key);
  }

  @Deprecated
  public static boolean isSuppressedFor(@NotNull PsiElement element, @NotNull LocalInspectionTool tool) {
    return BaseJavaBatchLocalInspectionTool.isSuppressedFor(element, tool);
  }
}
