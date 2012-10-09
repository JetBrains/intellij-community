// "Replace with method reference" "false"
class Test {
  public interface I {
    int m();
  }
  {
    I i = new <caret>I() {
      @Override
      public int m() {
        return m();
      }
    };

  }
}
