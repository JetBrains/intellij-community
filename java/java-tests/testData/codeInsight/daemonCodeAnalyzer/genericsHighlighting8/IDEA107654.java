class X<A extends X<A>> {
    static class Y<B extends  Y> extends X<<error descr="Type parameter 'B' is not within its bound; should extend 'X<B>'">B</error>> {}
}
