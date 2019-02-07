public class AnonField {
  private String xxx;

  public static void main(String[] args) {
    new AnonField().foo();
  }
  void foo() {
    String xxx = "local";
    new AnonField() {
      @Override
      public void foo() {
        System.out.println(<caret>xxx);
      }
    }.foo();
  }
}