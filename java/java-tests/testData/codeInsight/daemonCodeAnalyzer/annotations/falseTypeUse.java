import java.lang.annotation.*;

@interface Ann { }

class Foo {
  <K> @Ann String getFoo() {
    return null;
  }
}
class Example {

  public static class Foo {
    public interface Bar<T> {}
  }

  public static <T> @One Foo.Bar<T> test1(T t) { return null; }
  public static <T> <error descr="Static member qualifying type may not be annotated">@Two</error> Foo.Bar<T> test2(T t) { return null; }
  public static <T> @One Foo.Bar<T>[][] test3(T t) { return null; }

}
@Target(ElementType.METHOD)
@interface One {}

@Target(ElementType.TYPE_USE)
@interface Two {}