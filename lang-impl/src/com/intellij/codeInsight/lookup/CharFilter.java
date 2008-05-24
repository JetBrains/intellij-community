/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 2:18:55 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.lookup;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

public abstract class CharFilter {
  public static final ExtensionPointName<CharFilter> EP_NAME = ExtensionPointName.create("com.intellij.lookup.charFilter");

  public static enum Result {
    ADD_TO_PREFIX, SELECT_ITEM_AND_FINISH_LOOKUP, HIDE_LOOKUP
  }

  /**
   * Informs about further action on typing character c when completion lookup has specified prefix
   * @param c character being inserted
   * @param prefixLength
   *@param lookup @return further action
   */
  @Nullable
  public abstract Result acceptChar(char c, final int prefixLength, final Lookup lookup);
}
