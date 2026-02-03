public class FinalStaticFields {
  private final static Object w1;
  private final static Object w2 = new Object();
  private final Object w3;
  private final Object w4 = new Object();
  private final Object w5;
  static {
    w1 = new Object();
  }
  {
    w3 = new Object();
  }
  FinalStaticFields() {
    w5 = new Object();
  }
  public void test() {
    if (<warning descr="Condition 'w1 == null' is always 'false'">w1 == null</warning>) {
    }
    if (<warning descr="Condition 'w2 == null' is always 'false'">w2 == null</warning>) {
    }
    if (<warning descr="Condition 'w3 == null' is always 'false'">w3 == null</warning>) {
    }
    if (<warning descr="Condition 'w4 == null' is always 'false'">w4 == null</warning>) {
    }
    if (<warning descr="Condition 'w5 == null' is always 'false'">w5 == null</warning>) {
    }
  }
}