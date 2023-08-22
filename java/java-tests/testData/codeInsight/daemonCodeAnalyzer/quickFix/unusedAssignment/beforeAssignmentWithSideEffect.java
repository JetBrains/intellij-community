// "Remove redundant assignment" "true-preview"
class A {
  A a = null;
  String m(String str) {
    return str;
  }

  {
    String ss = "";

    System.out.println();

    s<caret>s = a.m(ss);
  }
}