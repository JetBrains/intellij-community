
import static java.util.Objects.toString;

class Foo {

  String go() {
    return toString<error descr="Expected no arguments but found 1">("foo")</error>;
  }

  public String toString() {
    return super.toString();
  }

  public static void main(String[] args) {
    System.out.println(new Foo().go());
  }
}