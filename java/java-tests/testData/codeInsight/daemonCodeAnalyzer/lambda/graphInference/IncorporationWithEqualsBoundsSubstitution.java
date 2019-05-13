abstract class Test {
  abstract <Tf extends String> Tf  foo(Class<Tf> c);
  abstract <Tf1>              Tf1 foo1(Class<Tf1> c);

  abstract <U> Class<? extends U>  bar(Class<U> clazz);
  abstract <U1>         Class<U1> bar1(Class<U1> clazz);

  {
    foo(bar(String.class));
    foo(bar1(String.class));
    foo1(bar(String.class));
    foo1(bar1(String.class));
  }
}
