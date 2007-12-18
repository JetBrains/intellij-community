/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 16, 2006
 * Time: 9:15:21 PM
 */
package com.intellij.psi.impl.source;

import com.intellij.util.CharTable;

public class IdentityCharTable implements CharTable {
  public CharSequence intern(final CharSequence text) {
    return text;
  }
}