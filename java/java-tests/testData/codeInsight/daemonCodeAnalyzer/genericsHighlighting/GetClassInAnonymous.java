class A {
    class B {
    }

    B foo() {
        return new B() {
            void bar() {
                 Class<? extends B> aClass1 = getClass();
            }
        };
    }
    
    
}
