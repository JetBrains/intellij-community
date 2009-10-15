interface A {
    int ABC;
}
interface B extends A{
    int ABC;
}

class Foo {
    {
        int x = B.A<caret>
    }
}
