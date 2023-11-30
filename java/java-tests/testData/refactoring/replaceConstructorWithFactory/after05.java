class A {
    A(int i) {
    }

    static A createA(int i) {
        return new A(i);
    }
}

class B extends A {
    B(int i) {
      super(i);
    }
}