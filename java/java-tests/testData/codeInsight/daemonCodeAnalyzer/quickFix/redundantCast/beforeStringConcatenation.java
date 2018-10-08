// "Remove redundant cast(s)" "true"
class Test {
  {
    String s = "" + (in<caret>t)//c1
    (1 + 2);
  }
}