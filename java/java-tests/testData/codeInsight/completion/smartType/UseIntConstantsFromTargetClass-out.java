class Foo {
  public static final int FOO_FOO;
  public static final int FOO_BAR;

  void foo(int x);
}

public class Bar {
    
    {
       new Foo().foo(Foo.FOO_BAR);<caret>
    }
}
