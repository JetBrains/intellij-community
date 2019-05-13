// "Create method 'foo'" "true"
class Test {
    <R, D> R foo(T<R, D> t, D data) {
        return t.foo(this, data);
    }
}

class T<R, D> {


    public R foo(Test test, D data) {
        <selection>return null;</selection>
    }
}