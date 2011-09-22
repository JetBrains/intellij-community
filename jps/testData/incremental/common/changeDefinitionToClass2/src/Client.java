class Client{
  public static void main(String[] args) {
    Server server = Factory.createServer();
    server.foo();
  }
}