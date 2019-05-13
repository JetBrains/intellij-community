// "Suppress for parameter" "true"
public class Test {
  private void run(@SuppressWarnings("unused") String sss) {
  }

  public static void main(String[] args) {
    new Test().run(null);
  }
}