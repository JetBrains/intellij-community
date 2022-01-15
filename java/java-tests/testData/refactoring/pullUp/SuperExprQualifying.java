 
class A {
    public boolean foo() {
        return Math.random() > 0.5;
    }
}

class B extends A {
}

class Test extends B {
    @Override
    public boolean f<caret>oo() {
        if (!super.foo()) {
            return false;
        }
        return Math.random() > 0.5;
    }
}