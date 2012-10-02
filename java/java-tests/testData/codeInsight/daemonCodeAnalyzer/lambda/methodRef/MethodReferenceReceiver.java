class MyTest {

    interface I {
       void _(MyTest receiver, Integer i);
    }

    void m(Integer i) {}

    public static void main(String[] args) {
        I i = MyTest :: m;
        i._(new MyTest(), 1);
    }
}