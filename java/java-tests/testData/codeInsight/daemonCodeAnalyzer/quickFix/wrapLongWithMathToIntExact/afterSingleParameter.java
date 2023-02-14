// "Adapt argument using 'Math.toIntExact()'" "true-preview"

public class Test {
  void m(int i) {

  }

  void method() {
    m(Math.toIntExact(10L));
  }

}
