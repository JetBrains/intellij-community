// "Wrap parameter using 'Math.toIntExact()'" "true"

public class Test {
  void m(int i) {

  }

  void method() {
    m(10<caret>L);
  }

}
