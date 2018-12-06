// "Make 'F.foo' static" "true"
abstract class F {
    abstract void foo();
}

class B {
    {
        F.f<caret>oo();
    }
}