class Foo {
  String xxx();
}

public class MyFirstTestClassFoo {

    {
       foo().xxx()<caret>
    }

    Foo foo(int a) {}
    Foo foo(String a) {}

}