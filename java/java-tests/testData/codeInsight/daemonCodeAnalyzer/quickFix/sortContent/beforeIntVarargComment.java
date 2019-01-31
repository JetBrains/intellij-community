// "Sort content" "true"

class X {
  public void x() {
    doTest("1 <caret>",
           "and", "between", "div", "eq", "false", "ge", "gt", "instanceof",
           "le", "lt", "matches", "mod", "ne", "new", "not", "null", "or", "true", //comment
           "T");
  }

  private void doTest(String... s) {
  }

}