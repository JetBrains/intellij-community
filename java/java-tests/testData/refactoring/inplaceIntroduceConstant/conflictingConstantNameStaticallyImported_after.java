package p;
import static p.Foo.FOO;
class Foo {
  public static final String FOO = "foo";
}
class Test {

    public static final String FOO1 = new String(FOO);

    public void method() {
    }
}
