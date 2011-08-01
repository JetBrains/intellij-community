class Client2{
  public static void main(String[] args) {
    Service service = new ServiceAdapter() {
      public void foo() {
      }
    };
  }
}