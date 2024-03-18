public class CompilationError {

  public static void main(String[] args) {
    int inlineMe = 3; // inline
    int dd = 4 + <caret>inlineMe;

    "Error".
  }
}