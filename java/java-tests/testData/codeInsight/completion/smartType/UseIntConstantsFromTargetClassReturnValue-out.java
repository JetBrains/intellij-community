class Foo {
  public static final int FOO_FOO;
  public static final int FOO_BAR;

  int foo();
}

public class Bar {
    
    {
       if (new Foo().foo() == Foo.FOO_BAR<caret>)
    }
}
