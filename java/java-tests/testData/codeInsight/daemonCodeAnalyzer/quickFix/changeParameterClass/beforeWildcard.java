// "Make 'a' implement 'b'" "true-preview"
interface b<T> {
    void f(T t);
}

class a {}

class X {
    void h(b<? super Integer> i) {

    }

    void g() {
        h(new a<caret>());
    }
}