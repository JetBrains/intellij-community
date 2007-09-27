/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 2:18:55 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.lookup;

public interface CharFilter {
  int ADD_TO_PREFIX = 0;
  int SELECT_ITEM_AND_FINISH_LOOKUP = 1;
  int HIDE_LOOKUP = 2;

  /**
   * Informs about further action on typing character c when completion lookup has specified prefix
   * @param c character being inserted
   * @param prefix current completion prefix
   * @return one of above constants
   */
  int accept(char c, final String prefix);
}
