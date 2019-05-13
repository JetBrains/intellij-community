// "Create inner class 'Foo'" "true"
public class Test {
  <R> void foo(Fo<caret>o<R, String> f){}

    private class Foo<R, T> {
    }
}