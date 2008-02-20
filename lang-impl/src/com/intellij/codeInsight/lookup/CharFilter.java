/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 2:18:55 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.extensions.ExtensionPointName;

public abstract class CharFilter {
  public static final ExtensionPointName<CharFilter> EP_NAME = ExtensionPointName.create("com.intellij.lookup.charFilter");

  public static enum Result {
    ADD_TO_PREFIX, SELECT_ITEM_AND_FINISH_LOOKUP, HIDE_LOOKUP
  }

  /**
   * Informs about further action on typing character c when completion lookup has specified prefix
   * @param c character being inserted
   * @param prefix current completion prefix
   * @param lookup
   * @return further action
   */
  @Nullable
  public abstract Result acceptChar(char c, @NotNull final String prefix, final Lookup lookup);
}
