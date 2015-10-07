import java.util.List;

class Example {
  public void example(List<Foo> foos) {
    foos.forEach(foo -> foo.bar = null);
  }
}

class Foo {
  public Long bar;
}
