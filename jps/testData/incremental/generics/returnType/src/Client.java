public class Client {
  public void bar() {
    GenericType<Integer> list = new Server().foo();
    System.out.println("list = " + list);;
  }

  public static void main(String[] args){
    new Client().bar();
  }
}