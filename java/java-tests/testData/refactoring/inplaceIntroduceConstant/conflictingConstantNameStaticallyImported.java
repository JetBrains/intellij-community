package p;
import static p.Foo.FOO;
class Foo {
  public static final String FOO = "foo";
}
class Test {
    public void method() {
      String f<caret>oo = new String(FOO);
    }
}
