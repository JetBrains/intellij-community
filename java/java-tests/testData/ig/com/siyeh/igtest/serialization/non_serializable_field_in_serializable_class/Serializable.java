import java.io.*;

class C implements Serializable {
  private Foo <warning descr="Non-serializable field 'foo' in a Serializable class">foo</warning>;
  private Bar bar;
}

record R(Foo <warning descr="Non-serializable field 'foo' in a Serializable class">foo</warning>, Bar bar) implements Serializable {
}

class Foo {
}

class Bar implements Serializable {
}