class MyTest {

    interface I {
        void m1(int i);
    }

    static class A {
        void m(int i) {}
    }

    static class B extends A {
        void m(int i) {
            I mh = super::m;
        }
    }

    public static void main(String[] args) {
        new B().m(10);
    }
}

class MyTestWithBoxing {
    interface I {
        void m1(Integer i);
    }

    static class A {
        void m(int i) {}
    }

    static class B extends A {
        {
            I s = super::m;
        }
        
        void m(int i) {
          super.m(i);
        }
    }
}

class MyTest1 {

    interface I {
        void m();
    }

    void call(I s) {}

    I i = <error descr="Cannot resolve symbol 'NonExistentType'">NonExistentType</error>::m;

    {
        call(<error descr="Cannot resolve symbol 'NonExistentType'">NonExistentType</error>::m);
    }
}

