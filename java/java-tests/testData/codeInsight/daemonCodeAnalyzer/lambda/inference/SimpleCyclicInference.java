class NoLambda {
    interface I<T> {
        void f(T t);
    }

    <Z> void bar(I<Z> iz) {
    }

    void bazz() {
        bar(null);
        bar(<error descr="Cyclic inference">(z)-> {System.out.println();}</error>);
    }
  
    static <T> T id(T i2) {return i2;}

    {
       id(<error descr="Cyclic inference">() -> {System.out.println("hi");}</error>);
       NoLambda.<Runnable>id(() -> {System.out.println("hi");});
    }
}