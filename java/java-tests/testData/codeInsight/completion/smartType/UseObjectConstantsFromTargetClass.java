class Foo {
  public static final String FOO_FOO;
  public static final String FOO_BAR;

  void foo(String x);
}

public class Bar {
    
    {
       new Foo().foo(FB<caret>)
    }
}
