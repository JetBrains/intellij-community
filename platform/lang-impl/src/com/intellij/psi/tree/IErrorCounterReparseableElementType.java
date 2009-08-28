package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

public abstract class IErrorCounterReparseableElementType extends IReparseableElementType {
  public static final int NO_ERRORS = 0;
  public static final int FATAL_ERROR = Integer.MIN_VALUE;

  public IErrorCounterReparseableElementType(@NonNls final String debugName, final Language language) {
    super(debugName, language);
  }

  public abstract int getErrorsCount(CharSequence seq, Project project);

  public boolean isParsable(CharSequence buffer, final Project project) {
    return getErrorsCount(buffer, project) == NO_ERRORS;
  }
}
