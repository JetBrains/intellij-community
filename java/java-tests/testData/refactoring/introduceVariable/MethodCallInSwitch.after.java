class A {
    int getContentElementType() {
        return 0;
    }

    void method() {
        final int i = getContentElementType();
        switch (i) {
            default: throw new IllegalArgumentException("Wrong content type: " + i);
        }
    }
}