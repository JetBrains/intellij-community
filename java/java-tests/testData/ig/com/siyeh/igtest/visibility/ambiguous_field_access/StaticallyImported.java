package p;
import static p.Bar.foo;
class Parent<T extends Bar> {
  protected static final String foo = "I am Parent";
}
class Child<T extends Bar> extends Parent<T> {
  public Child() {
    super();
    System.out.println(foo);
  }
}

abstract class Bar {
  protected static final String foo = "I am Bar";
}