
class A extends C {

}

class C {

    void foo() {
      C.D<String> d = new C.D<>();
    }

    static class D<T> {
    }
}
