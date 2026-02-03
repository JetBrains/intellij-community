class A {
    int getContentElementType() {
        return 0;
    }

    void method() {
        switch (<selection>getContentElementType()</selection>) {
            default: throw new IllegalArgumentException("Wrong content type: " + getContentElementType());
        }
    }
}