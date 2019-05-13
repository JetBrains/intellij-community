interface Functional {
  void a(String s);
}

interface NonFunctional {
  void b(String s);
  void c();
}

interface P<<warning descr="Type parameter 'T' is never used">T</warning>> {
  void subscribe(Functional f);
  void subscribe(NonFunctional nf);
}

class Test {
  void foo(P p) {
    p.subscribe(s -> {});
  }
}

interface FunctionalExact {
  void a();
}

interface NonFunctionalExact {
  void b();
  void c();
}

interface PExact<<warning descr="Type parameter 'T' is never used">T</warning>> {
  void subscribe(FunctionalExact f);
  void subscribe(NonFunctionalExact nf);
}

class TestExact {
  void foo(PExact p) {
    p.subscribe(() -> {});
  }
}