public class CompilationError {

  public static void main(String[] args) {
    int <caret>inlineMe = 3; // inline
    int dd = 4 + inlineMe;

    "Error".
  }
}