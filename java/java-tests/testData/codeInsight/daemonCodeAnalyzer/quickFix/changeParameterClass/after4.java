// "Make 'b' extend 'a'" "true"
class a {
    void f(a a) {
        b b = null;
        f(b);
    }
}
class b extends a <caret>{
}

