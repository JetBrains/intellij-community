class Main {

    interface Function<T, U> {
        T fun(U t);
    }

    interface Sortable<X> {
        Sortable<X> sort(Comparator<X> c);
    }

    static class Inner {
        String foo() { return ""; }
    }

    <T2, U2> Comparator<U2> comparing(Function<T2, U2> mapper) {  return null; }

    void testAssignmentContext(Sortable<Inner> sortable, boolean cond) {
        Comparator<Inner> comparing0 = comparing(Inner::foo);
        Comparator<Inner> comparing = comparing((p) -> p.foo());
        Sortable<Inner> p1 = sortable.sort(comparing);
        Sortable<Inner> p2 = sortable.sort(comparing(x->x.foo()));
        Sortable<Inner> p3 = sortable.sort(cond ? comparing(Inner::foo) : comparing(x -> x.foo()));
        Sortable<Inner> p4 =  sortable.sort((cond ? comparing(Inner::foo)  : comparing(x -> x.foo())));
    }

    void testMethodContext(Sortable<Inner> list, boolean cond) {
        testMethodContext(list.sort(comparing(Inner::foo)), true);
        testMethodContext(list.sort(comparing(x->x.foo())), true);
        testMethodContext(list.sort(cond ? comparing(Inner::foo) : comparing(x -> x.foo())), true);
        testMethodContext(list.sort((cond ? comparing(Inner::foo) : comparing(x -> x.foo()))), true);
    }

    interface Comparator<T> {
        int compare(T o1, T o2);
    }
}
