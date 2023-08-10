class X<T> {
  class Y extends <error descr="Class cannot inherit from its type parameter">T</error> {

  }

  void test(Y y) {
    if (<warning descr="Condition 'y instanceof T' is redundant and can be replaced with a null check">y instanceof <error descr="Class or array expected">T</error></warning>) {

    }
  }
}
