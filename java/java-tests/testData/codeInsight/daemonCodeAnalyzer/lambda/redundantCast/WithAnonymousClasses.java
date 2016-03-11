
class Y {
  Y(int x) {}
  Y bar() {
    return new Y((<warning descr="Casting '1' to 'int' is redundant">int</warning>) 1) {};
  }
}
