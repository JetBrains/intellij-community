// "Collapse into loop" "true"
class X {
  void test() {
      for (long l = 10L; l >= 0L; l -= 2L) {
          System.out.println(l);
      }
  }
}