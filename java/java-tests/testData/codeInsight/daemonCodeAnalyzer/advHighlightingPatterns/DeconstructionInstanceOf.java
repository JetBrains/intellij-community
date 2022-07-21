record Top(Child c1, Child c2) {}
record Child(I x, I y){}
record Wrong(int x) {}

sealed interface I permits A, B {}
final class A implements I {}
final class B implements I {}

public class Test {
  void test(Object o, Integer i){
    switch (o){
      if (o instanceof Child<error descr="<html>Expected 2 arguments but found 1</html>">(A a)</error>){ }
      if (o instanceof Child(A a, B b)){ }
      if (o instanceof Top(Child a, Child(<error descr="Incompatible types. Found: 'int', required: 'I'">int x</error>, I y))){ }
      if (o instanceof Top(Child <error descr="Variable 'a' is already defined in the scope">a</error>, Child(A <error descr="Variable 'a' is already defined in the scope">a</error>, I y))){ }
      if (o instanceof Top(Child a, <error descr="Incompatible types. Found: 'Wrong', required: 'Child'">Wrong(int x)</error>)){ }
      if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'Wrong'">i instanceof Wrong(int x)</error>) { }
    }
  }
}
