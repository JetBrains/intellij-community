// "Make 'foo' static" "true"
abstract class F {
    static void foo() {
    }
}

class B {
    {
        F.foo();
    }
}