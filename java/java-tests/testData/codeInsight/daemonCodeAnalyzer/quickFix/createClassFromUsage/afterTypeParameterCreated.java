// "Create class 'Foo'" "true-preview"
public class Test {
  <R> void foo(Foo<R, String> f){}
}

public class Foo<R, T> {
}