// "Change 's' type to 'O.f<java.lang.String>'" "false"

class O {

    f f() {
        return null;
    }

    class f <T> {
    }

    void g() {

        final f s = this.<<caret>String>f();

    }

}
