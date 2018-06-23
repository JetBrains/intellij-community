interface I {
  void m(int i, int j);
}

class Main {
  void foo() {
    I in = <error descr="Cannot mix 'var' and explicitly typed parameters in lambda expression">(var i, int j)</error> -> {};
    I in1 = (var i, <error descr="Cannot resolve symbol 'j'">j</error><error descr="Identifier expected">)</error> -> {};
  }
}