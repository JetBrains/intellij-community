interface I<A, B>  {
  B foo(A a);
}
class NoInferenceResult {

    <A, B> I<A, B> m(I<A, B>  f) { return null; }
    <T> void m1(T t) { }

    void test() {
        m<error descr="'m(I<java.lang.Object,java.lang.Object>)' in 'NoInferenceResult' cannot be applied to '(<lambda expression>)'">((String s1) ->  (String s2) ->  s1 + s2)</error>;

        m((String s1) -> s1.length());
        m((String s1) -> s1);

        m1<error descr="'m1(java.lang.Object)' in 'NoInferenceResult' cannot be applied to '(<lambda expression>)'">(() -> { })</error>; 
    }
}
