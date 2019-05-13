// "Wrap parameter using 'Math.toIntExact()'" "true"

public class Test {
  void m(int i) {

  }

  void method() {
    m(Math.toIntExact(10L));
  }

}
