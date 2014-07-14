import java.lang.Exception;

public class Foo {
  {
    foo(X<caret>)
  }

  void foo(Class<? extends Throwable> c) {}
}

interface XIntf {}
class XClass {}