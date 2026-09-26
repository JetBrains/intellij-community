class A {
    public A<caret>(int i) {
    }

    A method() {
        return new A(10);
    }
}

class B extends A {
    B(int j) {
        super(j+1);
    }
}

class Usage {
    A a = new A(2);
}