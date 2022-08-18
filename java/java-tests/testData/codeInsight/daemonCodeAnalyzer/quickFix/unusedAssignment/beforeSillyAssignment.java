// "Remove redundant assignment" "true-preview"
class A {
  {
    String ss = "";

    s<caret>s = ss;
  }
}