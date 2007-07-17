package com.intellij.codeInsight.hint.api;

import com.intellij.codeInsight.lookup.LookupItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @auhtor Maxim.Mossienko
 */
public interface ParameterInfoHandler <O,P> {
  boolean couldShowInLookup();
  @Nullable Object[] getParametersForLookup(LookupItem item, ParameterInfoContext context);
  @Nullable Object[] getParametersForDocumentation(P p, ParameterInfoContext context);

  @Nullable O findElementForParameterInfo(final CreateParameterInfoContext context);
  void showParameterInfo(@NotNull final O element, final CreateParameterInfoContext context);

  @Nullable O findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context);
  void updateParameterInfo(final @NotNull O o, final UpdateParameterInfoContext context);

  @Nullable String getParameterCloseChars();
  boolean tracksParameterIndex();
  void updateUI(P p, ParameterInfoUIContext context);
}
