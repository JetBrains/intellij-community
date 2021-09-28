interface DummyI {
    void doSth();
}

class DummyImpl implements DummyI {
    @Override
    public void doSth() {
        System.out.println("Hi");
    }
}

class ShownDependency {
    DummyImpl dummyImpl = new DummyImpl();

    void hi() {
        dummyImpl.doSth(); // Invoke via Class
    }
}

class InvisibleDependency {
    DummyI dummyI = new DummyImpl();

    void hi() {
        dummyI.doSth(); // Invoke via interface
    }
}