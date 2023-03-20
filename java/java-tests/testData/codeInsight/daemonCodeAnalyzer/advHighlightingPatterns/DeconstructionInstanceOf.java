record Top(Child c1, Child c2) {}
record Child(I x, I y){}
record Wrong(int x) {}
record TypedRecord<T>(T t) {}

sealed interface I permits A, B {}
final class A implements I {}
final class B implements I {}

public class Test {
  void test(Object o, Integer i){
    switch (o){
      if (o instanceof Child<error descr="Incorrect number of nested patterns: expected 2 but found 1">(A a)</error>){ }
      if (o instanceof Child(A a1, A a2, <error descr="Incorrect number of nested patterns: expected 2 but found 3">A a3)</error>) { }
      if (o instanceof Child(A a, B b)){ }
      if (o instanceof Top(Child a, Child(<error descr="Incompatible types. Found: 'int', required: 'I'">int x</error>, I y))){ }
      if (o instanceof Top(Child <error descr="Variable 'a' is already defined in the scope">a</error>, Child(A <error descr="Variable 'a' is already defined in the scope">a</error>, I y))){ }
      if (o instanceof Top(Child a, <error descr="Incompatible types. Found: 'Wrong', required: 'Child'">Wrong(int x)</error>)){ }
      if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'Wrong'">i instanceof Wrong(int x)</error>) { }
    }
  }

  <T> void testRawDeconstruction(TypedRecord<T> r) {
    if (r instanceof TypedRecord<T>(I t)){ }
    if (r instanceof TypedRecord<T>(T t)){ }
    if (r instanceof <error descr="Raw deconstruction patterns are not allowed">TypedRecord</error>(I t)){ }
    if (r instanceof <error descr="Raw deconstruction patterns are not allowed">TypedRecord</error>(T t)){ }
  }

  void resolveHighlighting1(Object o){
    if (o instanceof Child(A a, B b) c){
      System.out.println(a);
      System.out.println(c);
    }
    else {
      System.out.println(<error descr="Cannot resolve symbol 'a'">a</error>);
      System.out.println(<error descr="Cannot resolve symbol 'c'">c</error>);
    }
  }

  void resolveHighlighting2(Object o){
    if (!(o instanceof Child(A a, B b) c)){
      System.out.println(<error descr="Cannot resolve symbol 'a'">a</error>);
      System.out.println(<error descr="Cannot resolve symbol 'c'">c</error>);
    }
    else {
      System.out.println(a);
      System.out.println(c);
    }
  }

  void resolveHighlighting3(Object o){
    if (!(o instanceof Child(A a, B b) c)) return;
    System.out.println(a);
    System.out.println(c);
  }

  void resolveHighlighting4(Object o){
    if (o instanceof Child(A a, B b) c) return;
    System.out.println(<error descr="Cannot resolve symbol 'a'">a</error>);
    System.out.println(<error descr="Cannot resolve symbol 'c'">c</error>);
  }
}
