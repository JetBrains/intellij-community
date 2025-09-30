// "Fix the typo 'Null' to 'null'" "false"
public class Test {
  public static void main(String[] args) {
    call(Null<caret>);
  }

  public static void call(boolean b) {
  }
}