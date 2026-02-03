class A<T> {
    <S extends A<? extends T>> void foo(){}
    void bar(A<?> a){
        a.<<error descr="Type parameter 'A' is not within its bound; should extend 'A<? extends capture<?>>'">A<?></error>>foo();
    }
}
