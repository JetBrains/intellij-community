public class Subject {
  private int myInt;
  private int t;

  public void wp(String s, int <caret>p) {
    myInt += p;
  }

  void foo() {
      wp("s1", t);
      wp("s2", t);
  }
}