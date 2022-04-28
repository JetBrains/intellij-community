// "Move 'condition' into anonymous object" "true"
class Test {
  public void test() {
    Object condition;
    Runnable r = () -> {
      condition<caret> = new Object();
      condition.hashCode();
    };
  }
}
