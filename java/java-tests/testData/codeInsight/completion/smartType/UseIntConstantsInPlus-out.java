class Foo {
  public static final int FOO_FOO;
  public static final int FOO_BAR;

  Foo(int a) {}

  void foo(int x);
}

public class Bar {
    
    {
       new Foo(2 + Foo.FOO_BAR<caret>)
    }
}
