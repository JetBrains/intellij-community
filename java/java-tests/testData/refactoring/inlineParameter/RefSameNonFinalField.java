public class Subject {
  private int myInt;
  private int t;

  public void wp(int <caret>p) {
    myInt += p;
  }

  void foo() {
      wp(t);
      wp(t);
  }
}