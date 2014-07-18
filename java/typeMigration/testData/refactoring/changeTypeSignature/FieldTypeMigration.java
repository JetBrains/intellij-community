class Test {
    interface A<T> {
        void foo(T t);
    }

    class B implements A<Inte<caret>ger> {
        Integer str;
        public void foo(Integer s) {
            str = s;
        }
    }
}