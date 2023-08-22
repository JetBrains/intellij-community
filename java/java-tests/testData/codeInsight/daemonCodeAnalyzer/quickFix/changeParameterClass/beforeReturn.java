// "Make 'Foo' implement 'Foo.Bar'" "true-preview"
public class Foo {

  public Bar getBar() {
    return t<caret>his;
  }

  public interface Bar {}
}