class Test4 {
    void test() {
        Foo2<Test4> f = Test4::yyy;
    }
    static void yyy(Test4 anObject) {}
}
interface Foo2<T> {
    void bar(T j);
}