class Test {
  private <<error descr="Cyclic inheritance involving 'S'"></error>S extends K, K extends S> S b(S s) {

    if (true) return b <error descr="'b(S)' in 'Test' cannot be applied to '()'">()</error>;
  <error descr="Missing return statement">}</error>
}