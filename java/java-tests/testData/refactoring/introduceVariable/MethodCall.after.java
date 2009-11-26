class A {
    Object getObject() {
        return null;
    }

    void method() {
        final Object temp = getObject();
        Object o = temp;
        return temp.hashCode();
    }
}