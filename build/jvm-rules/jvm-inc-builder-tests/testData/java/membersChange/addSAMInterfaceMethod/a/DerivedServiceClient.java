public class DerivedServiceClient {
  public void execute() {
    Util.invokeDerivedService(() -> System.out.println("Hello2"));
  }
}