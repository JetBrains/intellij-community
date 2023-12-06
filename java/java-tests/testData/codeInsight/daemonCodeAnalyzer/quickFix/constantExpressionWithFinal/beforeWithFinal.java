// "Fix all 'Constant expression can be evaluated' problems in file" "true"
class Test {
  private final String field = "field";

  void test() {
    String foo = "Test"<caret> + "test2";
    String foo2 = "Test" + field;
    int i1 = 1 + 1;
    int i2 = 1 + -1;
    int i3 = Season.SPRING.i + 2;
    int i4 = Season.i2 + 2;
  }


  enum Season {
    SPRING(1);
    public static final int i2 = 2;
    public final int i;

    Season(int i) {
      this.i = i;
    }
  }
}