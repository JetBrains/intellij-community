public class ClientImpl extends Client {
  public Test foo() {
    return new Test();
  }

  public static void main(String[] args) {
    new ClientImpl();
  }

}