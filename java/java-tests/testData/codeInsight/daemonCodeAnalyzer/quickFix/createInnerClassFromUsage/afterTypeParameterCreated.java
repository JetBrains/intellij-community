// "Create inner class 'Foo'" "true-preview"
public class Test {
  <R> void foo(Fo<caret>o<R, String> f){}

    private class Foo<R, T> {
    }
}