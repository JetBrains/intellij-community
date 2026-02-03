// "Replace with lambda" "false"
class Test {
  public interface I {
    int m();
  }
  {
    I i = new <caret>I() {
      @Override
      public int m() {
        m();
        return 0;
      }
    };

  }
}
