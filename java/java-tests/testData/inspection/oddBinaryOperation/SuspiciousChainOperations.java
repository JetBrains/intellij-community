
class DomainObject {
  int a;
  int b;
  int c;
  int d;
  int f;
}

class OddBinaryOperation {
  void foo(DomainObject first, DomainObject second) {
    boolean b = first.a == second.a || first.b == second.b || first.c == second.c || first.d == second.d || first.f == <warning descr="Reference name is the same as earlier in chain">second.d</warning>;
  }

  void bar(DomainObject first, DomainObject second) {
    first.a = second.a;
    first.b = second.b;
    first.c = second.c;
    first.d = <warning descr="Reference name is the same as earlier in chain"><warning descr="Reference name is the same as earlier in chain">second.c</warning></warning>;
    first.f = second.f;
  }
}