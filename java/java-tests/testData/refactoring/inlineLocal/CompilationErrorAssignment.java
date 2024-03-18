public class CompilationError {

  public void test() {
    public static void main(String[] args) {
      int inlineMe = 2;
      <caret>inlineMe = 3; // inline
      int dd = 4 + inlineMe;

      "Error".
    }
  }
}