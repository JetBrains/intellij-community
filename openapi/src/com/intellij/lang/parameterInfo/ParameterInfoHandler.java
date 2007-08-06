package com.intellij.lang.parameterInfo;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ParameterInfoHandler <ParameterOwner, ParameterType> {
  boolean couldShowInLookup();
  @Nullable Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context);
  @Nullable Object[] getParametersForDocumentation(ParameterType p, ParameterInfoContext context);

  // Find element for parameter info should also set ItemsToShow in context and may set highlighted element
  @Nullable
  ParameterOwner findElementForParameterInfo(final CreateParameterInfoContext context);
  // Usually context.showHint
  void showParameterInfo(@NotNull final ParameterOwner element, final CreateParameterInfoContext context);

  // Null returns leads to removing hint
  @Nullable
  ParameterOwner findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context);
  void updateParameterInfo(final @NotNull ParameterOwner o, final UpdateParameterInfoContext context);

  // Can be null if parameter info does not track parameter index
  @Nullable String getParameterCloseChars();
  boolean tracksParameterIndex();

  // context.setEnabled / context.setupUIComponentPresentation
  void updateUI(ParameterType p, ParameterInfoUIContext context);
}
