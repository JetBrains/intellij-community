// "Replace '(String) obj' with 's'" "true-preview"

class X {
  void foo(Object obj) {
    String s = (String) obj;
    System.out.println(((String) ob<caret>j).trim());
  }
}