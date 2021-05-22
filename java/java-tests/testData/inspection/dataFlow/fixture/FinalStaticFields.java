public class FinalStaticFields {
  private final static Object w1;
  private final static Object w2 = new Object();
  static {
    w1 = new Object();
  }
  public void test() {
    if (<warning descr="Condition 'w1 == null' is always 'false'">w1 == null</warning>) {
    }
    if (<warning descr="Condition 'w2 == null' is always 'false'">w2 == null</warning>) {
    }
  }
}