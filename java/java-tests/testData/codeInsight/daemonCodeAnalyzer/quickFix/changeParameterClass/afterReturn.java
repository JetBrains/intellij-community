// "Make 'Foo' implement 'Foo.Bar'" "true"
public class Foo implements Foo.Bar {

  public Bar getBar() {
    return this;
  }

  public interface Bar {}
}