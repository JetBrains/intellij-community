class A {
  static int foo() {
    return <warning descr="'4 * 1 */* comment*/ 1 * 5' can be replaced with '4 * 5'"><caret>4 * 1 */* comment*/ 1 * 5</warning>;
  }
}