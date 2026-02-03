import java.io.*;

class C implements Serializable {
  private Foo <warning descr="Non-serializable field 'foo' in Serializable class">foo</warning>;
  private Bar bar;
}

record R(Foo <warning descr="Non-serializable component 'foo' in Serializable record">foo</warning>, Bar bar) implements Serializable {
}
enum E {
  A, B;
  private final Foo foo = new Foo();
}
class Foo {
}

class Bar implements Serializable {
}