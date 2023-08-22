// "Make 'F.foo()' static" "true-preview"
abstract class F {
    abstract void foo();
}

class B {
    {
        F.f<caret>oo();
    }
}