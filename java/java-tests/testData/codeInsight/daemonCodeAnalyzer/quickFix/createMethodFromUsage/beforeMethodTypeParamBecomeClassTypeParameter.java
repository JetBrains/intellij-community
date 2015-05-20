// "Create method 'foo'" "true"
class Test {
    <R, D> R foo(T<R, D> t, D data) {
        return t.f<caret>oo(this, data);
    }
}

class T<R, D> {


}