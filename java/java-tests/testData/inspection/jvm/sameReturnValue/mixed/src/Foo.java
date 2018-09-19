public class Foo extends Bar {

  public int xxx() {
    return 0;
  }

  public static void main(String[] args) {
    System.out.println(new Foo().xxx());
    System.out.println(new Bar().xxx());
  }
}