class InlineMethodCallingSuper {
    class Parent {
        void foo() {

        }
    }

    class Child extends Parent {
        void bar() {
            super.foo();
        }

        void callFooThroughBarOnC(Child c){
            c.b<caret>ar();
        }
    }
}
