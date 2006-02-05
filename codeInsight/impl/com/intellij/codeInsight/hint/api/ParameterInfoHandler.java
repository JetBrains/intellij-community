package com.intellij.codeInsight.hint.api;

import com.intellij.codeInsight.lookup.LookupItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 31, 2006
 * Time: 10:42:37 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ParameterInfoHandler <O,P> {
  boolean couldShowInLookup();
  @Nullable Object[] getParametersForLookup(LookupItem item, ParameterInfoContext context);

  @Nullable O findElementForParameterInfo(final CreateParameterInfoContext context);
  void showParameterInfo(@NotNull final O element, final CreateParameterInfoContext context);

  @Nullable O findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context);
  void updateParameterInfo(final @NotNull O o, final UpdateParameterInfoContext context);

  @NotNull String getParameterCloseChars();
  boolean tracksParameterIndex();
  void updateUI(P p, ParameterInfoUIContext context);
}
