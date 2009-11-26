class A {
    Object getObject() {
        return null;
    }

    void method() {
        Object o = <selection>getObject()</selection>;
        return getObject().hashCode();
    }
}