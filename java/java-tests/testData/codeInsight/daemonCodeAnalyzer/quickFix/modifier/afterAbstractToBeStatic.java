// "Make 'F.foo()' static" "true-preview"
abstract class F {
    static void foo() {
    }
}

class B {
    {
        F.foo();
    }
}