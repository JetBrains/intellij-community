abstract class A {
    A(int x){}
    abstract void foo();
    void test(A a){}

    {
        test(new A(<selection>x<caret></selection>) {
        });
    }
}