// "Fix the typo 'True' to 'true'" "false"
public class Test {
  public static void main(String[] args) {
    call(True<caret>);
  }

  public static void call(String b) {
  }
}