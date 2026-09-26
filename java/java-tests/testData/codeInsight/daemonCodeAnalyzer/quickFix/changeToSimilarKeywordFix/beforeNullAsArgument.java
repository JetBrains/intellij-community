// "Fix the typo 'Null' to 'null'" "true-preview"
public class Test {
  public static void main(String[] args) {
    call(Null<caret>);
  }

  public static void call(Object b) {
  }
}