@interface Anno {
    String value();
}

@Anno(A.AAA<caret>)
class A {
    public static final String AAA = "aaa";
}

