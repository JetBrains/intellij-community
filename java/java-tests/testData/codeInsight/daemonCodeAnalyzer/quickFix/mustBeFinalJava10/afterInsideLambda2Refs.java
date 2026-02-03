// "Move 'condition' into anonymous object" "true-preview"
class Test {
  public void test() {
      var ref = new Object() {
          Object condition;
      };
    Runnable r = () -> {
      ref.condition = new Object();
      ref.condition.hashCode();
    };
  }
}
