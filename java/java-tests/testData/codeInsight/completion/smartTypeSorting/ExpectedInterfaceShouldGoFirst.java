interface MyProcessor<T> {
  void execute(T t);
}

class Proc1<T> implements MyProcessor<T> { public void execute(T t) {}}

class Foo {
  MyProcessor<Foo> p = new <caret>
}