package xxxy.yy;

class  MyTest {

    {
        xxxy.yy.MyTest.I o;
        I i = new xxxy.yy.MyTest.I() {
            @Override
            public void foo() {
            }
        };
    }

    interface I {
        void foo();
    }
}