class MyTest {
    interface I<T> {
        void m();
    }

    private final I<String> i;
    
    public MyTest(I i) {
        this.i = i == null ? () -> {} : <warning descr="Unchecked assignment: 'MyTest.I' to 'MyTest.I<java.lang.String>'">i</warning>;
    }
}