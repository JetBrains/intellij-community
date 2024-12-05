import java.util.Objects;

record Test2(String x, String y) {
  static void test2(Test2 test2) {
    if (Objects.equals(test2.getSomething2(), test2.y())) {
      if(<warning descr="Condition 'Objects.equals(test2.x, test2.getSomething3())' is always 'true'">Objects.equals(test2.x, test2.getSomething3())</warning>) {


      }
      if (<warning descr="Condition 'Objects.equals(test2.getSomething(), test2.y)' is always 'true'">Objects.equals(test2.getSomething(), test2.y)</warning>) {

      }
    }

  }


  static void test(Test2 test2) {
    if (test2.getSomething2().equals(test2.y())) {
      if(<warning descr="Condition 'test2.x.equals(test2.getSomething3())' is always 'true'">test2.x.equals(test2.getSomething3())</warning>) { //it works


      }
      if (<warning descr="Condition 'test2.getSomething().equals(test2.y)' is always 'true'">test2.getSomething().equals(test2.y)</warning>) {

      }
    }

  }

  public String getSomething() {
    return x;
  }
  public String getSomething3() {
    return y;
  }
  public String getSomething2() {
    return x;
  }
}