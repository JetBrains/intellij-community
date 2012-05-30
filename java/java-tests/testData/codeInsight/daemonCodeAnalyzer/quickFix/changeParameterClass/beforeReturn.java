// "Make 'Foo' implement 'Foo.Bar'" "true"
public class Foo {

  public Bar getBar() {
    return t<caret>his;
  }

  public interface Bar {}
}