// "Fix the typo 'True' to 'true'" "true-preview"
public class Test {
  public static void main(String[] args) {
    call(True<caret>);
  }

  public static void call(boolean b) {
  }
}