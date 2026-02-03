import java.util.stream.Stream;

class A {
  private void test5(Integer i, String... strings) {}
  private void <warning descr="Private method 'test5(java.lang.Integer, java.lang.Integer, java.lang.String...)' is never used">test5</warning>(Integer i, Integer b, String... strings) {
    System.out.println(i);
    System.out.println(b);
    System.out.println(strings);
  }

  void p(Stream<Integer> stream){
    stream.forEach(this::test5);
  }
}