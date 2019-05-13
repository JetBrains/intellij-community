interface E { }

interface Named {
    String getName();
}
interface MyNamed extends Named {}

class X {
    void foo(E e) {
        if (e instanceof MyNamed && e.getn<caret>)
    }

}