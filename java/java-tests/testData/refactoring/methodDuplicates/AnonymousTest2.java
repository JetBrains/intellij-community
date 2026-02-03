class A {

    class I {
    }

    class I1 {
    }

    public void foo() {
        Object innerOne = new I1() {
        };
        innerOne.toString();
    }

    public void <caret>bar() {
        Object innerTwo = new I() {
        };
        innerTwo.toString();
    }
}