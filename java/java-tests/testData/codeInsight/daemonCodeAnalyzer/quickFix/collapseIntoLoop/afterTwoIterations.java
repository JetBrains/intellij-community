// "Collapse into loop" "true"
class X {
  void test() {
      for (int i : new int[]{12, 17}) {
          System.out.println(i);
      }
  }
}