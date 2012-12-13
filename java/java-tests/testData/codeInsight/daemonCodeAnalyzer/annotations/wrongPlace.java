import java.util.Collection;

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

class B extends <error descr="Annotations are not allowed here">@Deprecated</error> Object { }

enum E {
  @Anno E1
}

interface I {
  @<error descr="Duplicate annotation">Anno</error> public @<error descr="Duplicate annotation">Anno</error> Collection<<error descr="Annotations are not allowed here">@Anno</error> String> method(@<error descr="Duplicate annotation">Anno</error> @<error descr="Duplicate annotation">Anno</error> Object o);
}

@interface Caller {
  Anno anno() default @Anno;
}