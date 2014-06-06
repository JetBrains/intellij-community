/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Danila Ponomarenko
 */
public abstract class PriorityLocalQuickFixWrapper implements LocalQuickFix {
  private final LocalQuickFix fix;

  private PriorityLocalQuickFixWrapper(@NotNull LocalQuickFix fix) {
    this.fix = fix;
  }

  @NotNull
  @Override
  public String getName() {
    return fix.getName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return fix.getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    fix.applyFix(project, descriptor);
  }

  private static class HighPriorityLocalQuickFixWrapper extends PriorityLocalQuickFixWrapper implements HighPriorityAction {
    protected HighPriorityLocalQuickFixWrapper(@NotNull LocalQuickFix fix) {
      super(fix);
    }
  }

  private static class NormalPriorityLocalQuickFixWrapper extends PriorityLocalQuickFixWrapper {
    protected NormalPriorityLocalQuickFixWrapper(@NotNull LocalQuickFix fix) {
      super(fix);
    }
  }


  private static class LowPriorityLocalQuickFixWrapper extends PriorityLocalQuickFixWrapper implements LowPriorityAction {
    protected LowPriorityLocalQuickFixWrapper(@NotNull LocalQuickFix fix) {
      super(fix);
    }
  }

  @NotNull
  public static LocalQuickFix highPriority(@NotNull LocalQuickFix fix) {
    return new HighPriorityLocalQuickFixWrapper(fix);
  }

  @NotNull
  public static LocalQuickFix normalPriority(@NotNull LocalQuickFix fix) {
    return new NormalPriorityLocalQuickFixWrapper(fix);
  }

  @NotNull
  public static LocalQuickFix lowPriority(@NotNull LocalQuickFix fix) {
    return new LowPriorityLocalQuickFixWrapper(fix);
  }
}
