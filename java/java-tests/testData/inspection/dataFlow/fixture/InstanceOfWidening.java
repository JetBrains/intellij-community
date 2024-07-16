public class InstanceOfWidening {
  public static void test(Integer integer, Object o) {
    if (<warning descr="Condition 'integer instanceof Integer i' is redundant and can be replaced with a null check">integer instanceof Integer i</warning>) {
      System.out.println("1");
    }
    if (<warning descr="Condition 'integer instanceof Number i' is redundant and can be replaced with a null check">integer instanceof Number i</warning>) {
      System.out.println("1");
    }
    if (<warning descr="Condition 'integer instanceof Object i' is redundant and can be replaced with a null check">integer instanceof Object i</warning>) {
      System.out.println("1");
    }
    if (o instanceof Integer i1) {
      System.out.println("1");
    }
  }
}