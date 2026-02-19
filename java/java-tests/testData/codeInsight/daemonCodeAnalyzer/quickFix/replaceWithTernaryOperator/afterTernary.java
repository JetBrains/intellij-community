// "Replace with '(b ? foo(1) : foo(2)) != null ?:'" "true-preview"
class A {
  void bar(String s) {}

  void foo(boolean b){
    bar((b ? foo(1) : foo(2)) != null ? b ? foo(1) : foo(2) : null);
  }
  
  static String foo(int x) {
    return x > 0 ? "pos" : x < 0 ? "neg" : null;
  }
}