
import static java.util.Objects.toString;

class Foo {

  String go() {
    return toString<error descr="'toString()' in 'Foo' cannot be applied to '(java.lang.String)'">("foo")</error>;
  }

  public String toString() {
    return super.toString();
  }

  public static void main(String[] args) {
    System.out.println(new Foo().go());
  }
}