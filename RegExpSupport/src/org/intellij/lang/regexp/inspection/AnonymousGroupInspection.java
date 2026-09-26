// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpBackref;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Bas Leijdekkers
 */
public class AnonymousGroupInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new AnonymousGroupVisitor(holder);
  }

  private static class AnonymousGroupVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    AnonymousGroupVisitor(ProblemsHolder holder) {
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
      if (group.getNode().getLastChildNode().getElementType() != RegExpTT.GROUP_END) {
        return;
      }
      myHolder.registerProblem(group.getFirstChild(), RegExpBundle.message("inspection.warning.anonymous.capturing.group"));
    }

    @Override
    public void visitRegExpBackref(RegExpBackref backref) {
      final Collection<RegExpGroup.Type> types = RegExpLanguageHosts.getInstance().getSupportedNamedGroupTypes(backref);
      if (types.isEmpty()) {
        return;
      }
      myHolder.registerProblem(backref, RegExpBundle.message("inspection.warning.numeric.back.reference"));
    }
  }
}
