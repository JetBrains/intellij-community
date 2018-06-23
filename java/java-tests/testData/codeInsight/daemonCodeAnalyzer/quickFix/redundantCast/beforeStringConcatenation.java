// "Remove redundant cast(s)" "true"
class Test {
  {
    String s = "" + (in<caret>t) (1 + 2);
  }
}