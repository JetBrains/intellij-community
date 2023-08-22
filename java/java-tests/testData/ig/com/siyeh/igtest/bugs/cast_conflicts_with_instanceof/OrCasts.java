// IDEA-241354
class Test {
  interface A {
    String a();
  }
  interface B {
    String b();
  }

  void test2(Object obj) {
    if (obj instanceof A || isB(obj) && ((B) obj).b() != null) {}
  }
  private boolean isB(Object obj) {
    return obj instanceof B;
  }

  void test(Object obj) {
    if ((!(obj instanceof A) || ((A) obj).a() == null) && obj instanceof B) {
      System.out.println(((B) obj).b());
    }
  }
}