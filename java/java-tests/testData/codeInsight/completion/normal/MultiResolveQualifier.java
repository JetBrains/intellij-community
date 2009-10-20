class Foo {
  String xxx();
}

public class MyFirstTestClassFoo {

    {
       foo().xx<caret>
    }

    Foo foo(int a) {}
    Foo foo(String a) {}

}