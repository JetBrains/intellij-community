class Test {

    interface I<A, B> {
        B f(A a);
    }

    interface II<A, B> extends I<A,B> { }

    static class Foo<A> {
        boolean forAll(final I<A, Boolean> f) {
            return false;
        }

        String forAll(final II<A, String> f) {
            return "";
        }

        String forAll2(final II<A, String> f) {
            return "";
        }
    }

    void foo(Foo<String> as, final Foo<Character> ac) {
        boolean b1 = as.<error descr="Ambiguous method call: both 'Foo.forAll(I<String, Boolean>)' and 'Foo.forAll(II<String, String>)' match">forAll</error>(s -> ac.<error descr="Ambiguous method call: both 'Foo.forAll(I<Character, Boolean>)' and 'Foo.forAll(II<Character, String>)' match">forAll</error>(c -> false));
        String s1 = as.<error descr="Ambiguous method call: both 'Foo.forAll(I<String, Boolean>)' and 'Foo.forAll(II<String, String>)' match">forAll</error>(s -> ac.<error descr="Ambiguous method call: both 'Foo.forAll(I<Character, Boolean>)' and 'Foo.forAll(II<Character, String>)' match">forAll</error>(c -> ""));
        boolean b2 = as.<error descr="Ambiguous method call: both 'Foo.forAll(I<String, Boolean>)' and 'Foo.forAll(II<String, String>)' match">forAll</error>(s -> ac.<error descr="Ambiguous method call: both 'Foo.forAll(I<Character, Boolean>)' and 'Foo.forAll(II<Character, String>)' match">forAll</error>(c -> ""));
        String s2 = as.forAll2(s -> ac.forAll2(c -> <error descr="Bad return type in lambda expression: boolean cannot be converted to String">false</error>));
        boolean b3 = as.forAll((I<String, Boolean>)s -> ac.forAll((I<Character, Boolean>)c -> <error descr="Bad return type in lambda expression: String cannot be converted to Boolean">""</error>));
        String s3 = as.forAll((II<String, String>)s -> ac.forAll((II<Character, String>)c -> <error descr="Bad return type in lambda expression: boolean cannot be converted to String">false</error>));
    }
}
