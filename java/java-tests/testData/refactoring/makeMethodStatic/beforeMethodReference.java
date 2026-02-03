class Test4 {
    void test() {
        Foo2<Test4> f = Test4::yyy;
    }
    void yy<caret>y() {}
}
interface Foo2<T> {
    void bar(T j);
}