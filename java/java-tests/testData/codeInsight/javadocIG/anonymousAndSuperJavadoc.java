class Bar {
    public static void main(String[] args) {
        new Foo() {
            @Override
            public void foo() {

            }
        };
    }
}

interface Foo {
    /**
     * some javadoc
     */
    void foo();

}
