class Test {
    interface A<T> {
        T foo();
    }

    class B implements A<Stri<caret>ng> {
        public String foo() {
            return null;
        }

        public void bar() {
            String s = foo();
        }
    }

}