class Main {
  class <error descr="'var' is a restricted identifier and cannot be used for type declarations">var</error> {}

  <<error descr="'var' is a restricted identifier and cannot be used for type declarations">var</error> extends String> void foo() {}

  class Usage {
    <error descr="Illegal reference to restricted type 'var'">var</error> field = new <error descr="Illegal reference to restricted type 'var'">var</error>();
  }
}