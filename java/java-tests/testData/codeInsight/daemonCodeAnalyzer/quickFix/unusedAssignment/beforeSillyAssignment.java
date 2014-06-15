// "Remove redundant assignment" "true"
class A {
  {
    String ss = "";

    s<caret>s = ss;
  }
}