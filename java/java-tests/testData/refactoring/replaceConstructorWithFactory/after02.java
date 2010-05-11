class A {
    A(int i) {
    }

    public static A newA(int i) {
        return new A(i);
    }

    A method() {
        return newA(10);
    }
}

class B extends A {
    B(int j) {
        super(j+1);
    }
}

class Usage {
    A a = A.newA(2);
}