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
        boolean b1 = as.forAll<error descr="Ambiguous method call: both 'Foo.forAll(I<String,Boolean>)' and 'Foo.forAll(II<String,String>)' match">(s -> ac.forAll<error descr="Ambiguous method call: both 'Foo.forAll(I<Character,Boolean>)' and 'Foo.forAll(II<Character,String>)' match">(c -> false)</error>)</error>;
        String s1 = as.forAll<error descr="Ambiguous method call: both 'Foo.forAll(I<String,Boolean>)' and 'Foo.forAll(II<String,String>)' match">(s -> ac.forAll<error descr="Ambiguous method call: both 'Foo.forAll(I<Character,Boolean>)' and 'Foo.forAll(II<Character,String>)' match">(c -> "")</error>)</error>;
        boolean b2 = as.forAll<error descr="Ambiguous method call: both 'Foo.forAll(I<String,Boolean>)' and 'Foo.forAll(II<String,String>)' match">(s -> ac.forAll<error descr="Ambiguous method call: both 'Foo.forAll(I<Character,Boolean>)' and 'Foo.forAll(II<Character,String>)' match">(c -> "")</error>)</error>;
        String s2 = as.forAll2(s -> ac.forAll2(<error descr="Incompatible return type boolean in lambda expression">c -> false</error>));
        boolean b3 = as.forAll((I<String, Boolean>)s -> ac.forAll((I<Character, Boolean>)<error descr="Incompatible return type String in lambda expression">c -> ""</error>));
        String s3 = as.forAll((II<String, String>)s -> ac.forAll((II<Character, String>)<error descr="Incompatible return type boolean in lambda expression">c -> false</error>));
    }
}
