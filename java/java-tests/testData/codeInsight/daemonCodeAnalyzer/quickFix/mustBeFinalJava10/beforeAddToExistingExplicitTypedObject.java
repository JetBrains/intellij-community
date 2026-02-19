// "Move 'x' into anonymous object" "true-preview"
class Test {
  public void test() {
    Object ref = new /*1*/ Object() {
    };
    int x = 12;
    Runnable r = () -> {
            <caret>x++;
    };s
  }
}
