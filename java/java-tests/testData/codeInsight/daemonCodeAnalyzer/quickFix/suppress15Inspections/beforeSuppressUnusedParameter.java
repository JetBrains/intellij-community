// "Suppress for parameter" "true"
public class Test {
  private void run(String s<caret>ss) {
  }

  public static void main(String[] args) {
    new Test().run(null);
  }
}