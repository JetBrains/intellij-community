package com.intellij.codeInspection;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 04-Jan-2006
 */
public class CommonProblemDescriptorImpl implements CommonProblemDescriptor {
  private static final Logger LOG = Logger.getInstance(CommonProblemDescriptorImpl.class);
  private final QuickFix[] myFixes;
  private final String myDescriptionTemplate;

  public CommonProblemDescriptorImpl(final QuickFix[] fixes, @NotNull final String descriptionTemplate) {
    if (fixes == null) {
      myFixes = null;
    }
    else if (fixes.length == 0) {
      myFixes = QuickFix.EMPTY_ARRAY;
    }
    else {
      // no copy in most cases
      myFixes = ArrayUtil.contains(null, fixes) ? ContainerUtil.mapNotNull(fixes, FunctionUtil.<QuickFix>id(), QuickFix.EMPTY_ARRAY) : fixes;
      if (!(this instanceof ProblemDescriptor)) {
        for (QuickFix fix : fixes) {
          if (fix instanceof LocalQuickFix) {
            LOG.error("Local quick fix expect ProblemDescriptor, but here only CommonProblemDescriptor available");
          }
        }
      }
    }
    myDescriptionTemplate = descriptionTemplate;
  }

  @Override
  @NotNull
  public String getDescriptionTemplate() {
    return myDescriptionTemplate;
  }

  @Override
  @Nullable
  public QuickFix[] getFixes() {
    return myFixes;
  }

  public String toString() {
    return myDescriptionTemplate;
  }
}
