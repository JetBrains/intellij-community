import java.util.List;

class Example {
  public void example(List<Foo> foos) {
    foos.forEach(foo -> foo.bar = <warning descr="Assigning 'null' value to non-annotated field">null</warning>);
  }
}

class Foo {
  public Long bar;
}
