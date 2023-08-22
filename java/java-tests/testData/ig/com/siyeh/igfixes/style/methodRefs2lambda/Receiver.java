public class MyTest {
    interface I {
        void m(MyTest receiver);
    }

    void m() { }

    {
        I i = (I) MyTest::m;
        s.m(this);
    }

    static {
        I i = (I)MyTest:<caret>:m;
    }
}
