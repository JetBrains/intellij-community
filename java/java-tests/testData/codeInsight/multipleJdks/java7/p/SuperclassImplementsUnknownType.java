package p;
abstract class B extends A {
  {
    <error descr="Cannot resolve method 'filter(null)'">filter</error>(null);
  }
}

<error descr="Cannot access java.util.stream.Stream">abstract class C extends A</error>{}