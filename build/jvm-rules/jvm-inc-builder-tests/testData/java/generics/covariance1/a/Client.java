public class Client {
  public static void main(String[] args) {
    Base obj = new Derived();
    System.out.println(obj.getValue());
  }
}