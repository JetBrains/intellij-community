public abstract class Foo {
    void m() {
        new Foo()<caret>
    }
}

class FooBar1 { private FooBar1() { } }
class FooBar2 { private FooBar2(int x) { } }
class FooBar3 { public FooBar3(int x) { } private FooBar3() { } }
class FooBar4 { private FooBar4(int x) { } public FooBar4() { } }