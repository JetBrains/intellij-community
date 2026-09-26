class Test {
  private <<error descr="Cyclic inheritance involving 'S'"></error>S extends K, K extends S> S b(S s) {

    if (true) return b <error descr="Expected 1 argument but found 0">()</error>;
  <error descr="Missing return statement">}</error>
}