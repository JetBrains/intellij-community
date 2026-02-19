// "Move 'x' into anonymous object" "true-preview"
class Test {
  public void test() {
    var ref = new /*1*/ Object() {
        int x = 12;
    };
      Runnable r = () -> {
            ref.x++;
    };s
  }
}
