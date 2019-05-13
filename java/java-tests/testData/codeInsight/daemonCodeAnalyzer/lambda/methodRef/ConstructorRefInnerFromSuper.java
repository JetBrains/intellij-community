class MyTest {

    static class Super {
        class Inner {
            Inner(int i){}
        }
    }

    static class Child extends Super {

        interface I { 
          Inner m(Child child, int i); 
        }

        void test() {
            I var = Child.Inner::<error descr="Cannot resolve constructor 'Inner'">new</error>;
        }
    }
}
