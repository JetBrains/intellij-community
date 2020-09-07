package com.intellij.codeInspection;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommonProblemDescriptorImpl implements CommonProblemDescriptor {
  private static final Logger LOG = Logger.getInstance(CommonProblemDescriptorImpl.class);
  private final QuickFix<?>[] myFixes;
  private final @InspectionMessage String myDescriptionTemplate;

  public CommonProblemDescriptorImpl(QuickFix<?> @Nullable [] fixes, @NotNull @InspectionMessage String descriptionTemplate) {
    if (fixes != null && fixes.length > 0) {
      myFixes = ArrayUtil.contains(null, fixes) ? ContainerUtil.mapNotNull(fixes, FunctionUtil.id(), ArrayUtil.newArray(ArrayUtil.getComponentType(fixes), 0))
                                                : fixes;
      if (!(this instanceof ProblemDescriptor)) {
        for (QuickFix<?> fix : fixes) {
          if (fix instanceof LocalQuickFix) {
            LOG.error("Local quick fix expect ProblemDescriptor, but here only CommonProblemDescriptor available");
          }
        }
      }
    }
    else {
      myFixes = fixes;
    }
    myDescriptionTemplate = descriptionTemplate;
  }

  @Override
  @NotNull
  public @InspectionMessage String getDescriptionTemplate() {
    return myDescriptionTemplate;
  }

  @Override
  public QuickFix<?> @Nullable [] getFixes() {
    return myFixes;
  }

  @Override
  public String toString() {
    return myDescriptionTemplate;
  }
}
