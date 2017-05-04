/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.psi.RegExpBackref;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Bas Leijdekkers
 */
public class AnonymousGroupInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Anonymous capturing group or numeric back reference";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new AnonymousGroupVisitor(holder);
  }

  private static class AnonymousGroupVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    public AnonymousGroupVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpGroup(RegExpGroup group) {
      if (group.getType() != RegExpGroup.Type.CAPTURING_GROUP) {
        return;
      }
      final Collection<RegExpGroup.Type> types = RegExpLanguageHosts.getInstance().getSupportedNamedGroupTypes(group);
      if (types.isEmpty()) {
        return;
      }
      myHolder.registerProblem(group.getFirstChild(), "Anonymous capturing group");
    }

    @Override
    public void visitRegExpBackref(RegExpBackref backref) {
      final Collection<RegExpGroup.Type> types = RegExpLanguageHosts.getInstance().getSupportedNamedGroupTypes(backref);
      if (types.isEmpty()) {
        return;
      }
      myHolder.registerProblem(backref, "Numeric back reference");
    }
  }
}
