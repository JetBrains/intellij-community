class A {
    A(int i) {
    }

    public static A createA(int i) {
        return new A(i);
    }

    A method() {
        return createA(10);
    }
}

class B extends A {
    B(int j) {
        super(j+1);
    }
}

class Usage {
    A a = A.createA(2);
}