class Client{
  public static void main(String[] args) {
    Server server = new Server() {
      public void foo() {
        System.out.println("foo called");
      }
    };
    server.foo();
  }
}