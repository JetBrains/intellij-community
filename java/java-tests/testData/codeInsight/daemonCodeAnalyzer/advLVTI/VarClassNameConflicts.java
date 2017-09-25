class Main {
  class <error descr="'var' is a restricted local variable type and cannot be used for type declarations">var</error> {}

  <<error descr="'var' is a restricted local variable type and cannot be used for type declarations">var</error> extends String> void foo() {}
}