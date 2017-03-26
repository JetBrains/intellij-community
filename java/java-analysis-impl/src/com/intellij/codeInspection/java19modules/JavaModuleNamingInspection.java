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
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaModuleNamingInspection extends BaseJavaLocalInspectionTool {
  private final Pattern TRAILING_DIGITS = Pattern.compile("^([^0-9]+)\\d+$");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return !PsiUtil.isModuleFile(holder.getFile()) ? PsiElementVisitor.EMPTY_VISITOR : new JavaElementVisitor() {
      @Override
      public void visitModule(PsiJavaModule module) {
        super.visitModule(module);
        PsiJavaModuleReferenceElement nameElement = module.getNameIdentifier();
        String name = nameElement.getReferenceText();
        Matcher matcher = TRAILING_DIGITS.matcher(name);
        if (matcher.matches()) {
          String message = InspectionsBundle.message("inspection.java.module.naming.terminal.digits", name);
          holder.registerProblem(nameElement, message, QuickFixFactory.getInstance().createRenameElementFix(module, matcher.group(1)));
        }
      }
    };
  }
}