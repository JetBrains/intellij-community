class Foo {
  public S<caret> get() {
    return new MyStringBuffer();
  }
}

class MyStringBuffer extends StringBuffer implements SomeInterface<String> {}
interface SomeInterface<T> {}
class SomeOtherClass {}