
class Main {
  private String s = "";

  void f(Object o) {
    if (o instanceof (CharSequence cs && cs instanceof String s)) {
      System.out.println(<caret>s);
    }
  }
}