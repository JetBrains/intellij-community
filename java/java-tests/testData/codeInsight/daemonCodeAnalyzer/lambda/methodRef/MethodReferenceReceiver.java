class MyTest {
    interface I {
       void m(MyTest receiver, Integer i);
    }

    void m(Integer i) {}

    public static void main(String[] args) {
        I i = MyTest :: m;
        i.m(new MyTest(), 1);
    }
}