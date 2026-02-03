public class ServiceClient {
  public void execute() {
    Util.invokeService(() -> System.out.println("Hello"));
  }
}