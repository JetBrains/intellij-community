// "Replace method reference with lambda" "true-preview"
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
        I i = (I) myTest -> myTest.m();
    }
}
