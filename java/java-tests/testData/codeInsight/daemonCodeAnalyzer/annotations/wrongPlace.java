@interface Anno {
  Anno[] nested() default {};
}

abstract class C {
  abstract void f();

  void wrong() {
    <error descr="Annotations are not allowed here">@Anno</error> f();
  }

  @Anno(nested = {@Anno, @Anno})
  void notWrong() { }
}