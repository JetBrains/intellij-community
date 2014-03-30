import java.util.Collection;

@interface Anno {
  <error descr="Cyclic annotation element type">Anno[]</error> nested() default {};
}

abstract class C {
  abstract void f();

  void wrong() {
    <error descr="Annotations are not allowed here">@Anno</error> f();
  }

  @Anno(nested = {@Anno, @Anno})
  void notWrong() { }
}

class B extends <error descr="Type annotations are not supported at this language level">@Deprecated</error> Object { }

enum E {
  @Anno E1
}

interface I {
  @<error descr="Duplicate annotation">Anno</error>
  public @<error descr="Duplicate annotation">Anno</error>
   Collection<<error descr="Type annotations are not supported at this language level">@Anno</error> String>
  method(@<error descr="Duplicate annotation">Anno</error> @<error descr="Duplicate annotation">Anno</error> Object o);
}

@interface Caller {
  Anno anno() default @Anno;
}

@interface AnnoArray {
  @interface Part { }

  Part[] arrayValue() default {@Part, @Part};
}
