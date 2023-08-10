// "Sort content" "true-preview"

interface A {

  // @formatter:off

  int TO_UPPER       = 0;
  int TO_LOWER       = 1;
  int DO_NOT_CHANGE  = 2;
  int TO_TITLE       = 5;

  @Anno(intValues = {TO_UPPER, <caret>TO_LOWER, TO_TITLE, DO_NOT_CHANGE})
  void foo();

  @interface Anno {
    int[] intValues();
  }

}
