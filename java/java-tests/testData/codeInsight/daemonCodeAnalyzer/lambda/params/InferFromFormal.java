class IDEA101176 {

    static {
       foo((Integer i) -> i > 60);
    }

    static <T> void foo(Predicate<? super T> p){}
    interface Predicate<T> {
        public boolean test(T t);
    }
}