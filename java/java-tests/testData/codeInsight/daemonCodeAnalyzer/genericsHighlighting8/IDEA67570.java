interface A1
{
    String foo();
}

interface B1
{
    Object foo();
}

class C1<T extends B1 & A1> {
    void bar(T x) {
        String foo = x.foo();
    }
}

class C2<T extends A1 & B1> {
    void bar(T x) {
        String foo = x.foo();
    }
}
