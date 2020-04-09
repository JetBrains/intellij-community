// nullable variable with flow statement
class K {
    int f(Object o) {
        <selection>if (o == null) return 0;
        o = new Object();</selection>
        Object oo = o;

        return 1;
    }
}
