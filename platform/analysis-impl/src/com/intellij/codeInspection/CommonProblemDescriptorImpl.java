// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class CommonProblemDescriptorImpl implements CommonProblemDescriptor {
  private static final Logger LOG = Logger.getInstance(CommonProblemDescriptorImpl.class);
  private final QuickFix<?> @NotNull [] myFixes;
  private final @InspectionMessage String myDescriptionTemplate;

  CommonProblemDescriptorImpl(@NotNull @InspectionMessage String descriptionTemplate, @NotNull QuickFix<?> @Nullable [] fixes) {
    if (fixes != null && fixes.length > 0) {
      if (ArrayUtil.contains(null, fixes)) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          // throw only in the test mode for now, to avoid irrecoverable failures in plugins
          throw new IllegalArgumentException("'fixes' argument must not contain null elements, for consistency and performance reasons, but got: `"
                                             + Arrays.asList(fixes) + "'");
        }
        fixes = ContainerUtil.mapNotNull(fixes, FunctionUtil.id(), ArrayUtil.newArray(ArrayUtil.getComponentType(fixes), 0));
      }
      if (!(this instanceof ProblemDescriptor)) {
        for (QuickFix<?> fix : fixes) {
          if (fix instanceof LocalQuickFix) {
            LOG.error("Local quick fix expect ProblemDescriptor, but here only CommonProblemDescriptor available: " +
                      this.getClass().getName() + "; descr: " + descriptionTemplate);
          }
        }
      }
    }
    myFixes = fixes == null || fixes.length == 0 ? LocalQuickFix.EMPTY_ARRAY : fixes;
    myDescriptionTemplate = descriptionTemplate;
  }

  @Override
  public @NotNull @InspectionMessage String getDescriptionTemplate() {
    return myDescriptionTemplate;
  }

  @Override
  public @NotNull QuickFix<?> @Nullable [] getFixes() {
    return myFixes;
  }

  @Override
  public String toString() {
    return myDescriptionTemplate;
  }
}
