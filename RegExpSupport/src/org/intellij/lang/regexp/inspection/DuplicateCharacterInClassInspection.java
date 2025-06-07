// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class DuplicateCharacterInClassInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new DuplicateCharacterInClassVisitor(holder);
  }

  private static class DuplicateCharacterInClassVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    DuplicateCharacterInClassVisitor(@NotNull ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpClass(RegExpClass regExpClass) {
      PsiFile file = regExpClass.getContainingFile();
      if (file == null || InjectedLanguageManager.getInstance(file.getProject()).isFrankensteinInjection(file)) return;
      final HashSet<Object> seen = new HashSet<>();
      for (RegExpClassElement element : regExpClass.getElements()) {
        checkForDuplicates(element, seen);
      }
    }

    private void checkForDuplicates(RegExpClassElement element, Set<Object> seen) {
      if (element instanceof RegExpChar regExpChar) {
        final int value = regExpChar.getValue();
        if (value != -1 && !seen.add(value)) {
          myHolder.registerProblem(regExpChar,
                                   RegExpBundle.message("warning.duplicate.character.0.inside.character.class", regExpChar.getText()),
                                   new DuplicateCharacterInClassFix(regExpChar));
        }
      }
      else if (element instanceof RegExpSimpleClass regExpSimpleClass) {
        final RegExpSimpleClass.Kind kind = regExpSimpleClass.getKind();
        if (!seen.add(kind)) {
          final String text = regExpSimpleClass.getText();
          myHolder.registerProblem(regExpSimpleClass,
                                   RegExpBundle.message("warning.duplicate.predefined.character.class.0.inside.character.class", text),
                                   new DuplicateCharacterInClassFix(regExpSimpleClass));
        }
      }
    }
  }

  private static final class DuplicateCharacterInClassFix extends PsiUpdateModCommandQuickFix {

    private final String myText;

    private DuplicateCharacterInClassFix(RegExpElement predefinedCharacterClass) {
      myText = predefinedCharacterClass.getText();
    }

    @Override
    public @NotNull String getName() {
      return RegExpBundle.message("inspection.quick.fix.remove.duplicate.0.from.character.class", myText);
    }

    @Override
    public @NotNull String getFamilyName() {
      return RegExpBundle.message("inspection.quick.fix.remove.duplicate.element.from.character.class");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      element.delete();
    }
  }
}
