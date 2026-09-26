@foo.NonnullByDefault
class C {
    Object o;
    @javax.annotation.Nonnull String s;

    C(Object o, String s) {<caret>
        this.o = o;
        this.s = s;
    }
}