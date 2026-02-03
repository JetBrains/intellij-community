// "Make 'Foo' implement 'Foo.Bar'" "false"
public class Foo {

  public void getBar() {
    Bar f = new F<caret>oo();
  }

  public class Bar extends Foo {
  }
}