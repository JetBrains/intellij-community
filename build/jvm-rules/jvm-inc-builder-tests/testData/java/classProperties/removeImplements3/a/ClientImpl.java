public class ClientImpl extends Client {
  public Runnable foo() {
    return new Test();
  }

  public static void main(String[] args) {
    new ClientImpl();
  }

}