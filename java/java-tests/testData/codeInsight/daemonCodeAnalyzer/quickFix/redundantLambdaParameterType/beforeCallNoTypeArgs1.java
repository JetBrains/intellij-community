// "Remove redundant types" "false"
class NoInferenceResult {
    interface I<A, B>  {
      B foo(A a);
    }

    <A, B> I<A, B> m(I<A, B>  f) { return null; }

    void test() {
        m((Stri<caret>ng s1) -> s1.length());
        m((String s1) -> s1);
    }
}