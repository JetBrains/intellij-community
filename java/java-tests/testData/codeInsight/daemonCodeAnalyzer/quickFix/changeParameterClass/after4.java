// "Make 'b' extend 'a'" "true-preview"
class a {
    void f(a a) {
        b b = null;
        f(b);
    }
}
class b extends a <caret>{
}

