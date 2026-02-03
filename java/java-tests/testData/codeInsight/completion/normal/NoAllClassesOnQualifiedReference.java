interface A {
}
interface AxBxCx extends A{
}

class Foo {
    {
        A.ABC<caret>
    }
}
