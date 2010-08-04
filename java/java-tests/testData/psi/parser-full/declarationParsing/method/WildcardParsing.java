class C {
    List<? extends B> x(Collection<? super B> x) {
        X<?> x = new X<B>();
        X<? super Z<? extends B>> k;
    }
}