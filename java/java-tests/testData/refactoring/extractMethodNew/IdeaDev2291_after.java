class Tester {
    void method(Object... array) {
        Object object = null;
        newMethod(array, object);
    }

    private void newMethod(Object[] array, Object object) {
        array.equals(object);
    }
}