// "Remove redundant cast(s)" "true-preview"
class Test {
  {
    String s = "" + ((in<caret>t)//c1
    (1 - 2));
  }
}