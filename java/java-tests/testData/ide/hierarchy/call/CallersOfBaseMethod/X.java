package p;

class BaseClass {
    public void method() {} // Invoke 'Call Hierarchy'
}

class SubClassA extends BaseClass {
    @Override public void method() {}

    public void otherMethod() {
        method();
    }
}

class SubClassB extends BaseClass {
    @Override public void method() {}

    public void otherMethod() {
        method();
    }
}