class C {
    void foo(Object o) {
        <selection>((Runnable)(() -> System.out.println()))</selection>.run();
    }
}