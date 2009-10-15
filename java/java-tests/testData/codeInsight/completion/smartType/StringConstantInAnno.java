@interface Anno {
    String value();
}

@Anno(A.<caret>)
class A {
    public static final String AAA = "aaa";
}

