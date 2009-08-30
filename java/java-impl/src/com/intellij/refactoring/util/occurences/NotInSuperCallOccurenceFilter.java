package com.intellij.refactoring.util.occurences;



/**
 * @author dsl
 */
public class NotInSuperCallOccurenceFilter extends NotInSuperOrThisCallFilterBase {
  public static final OccurenceFilter INSTANCE = new NotInSuperCallOccurenceFilter();

  protected String getKeywordText() {
    return "super";
  }
}
