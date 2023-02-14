// "Move 'condition' into anonymous object" "true-preview"
class Test {
  public void test() {
    Object condition;
    Runnable r = () -> {
      condition<caret> = new Object();
      condition.hashCode();
    };
  }
}
