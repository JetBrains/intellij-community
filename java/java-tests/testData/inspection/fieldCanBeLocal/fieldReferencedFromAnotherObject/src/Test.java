class G {
    private G foo;
    private int bar;

    public G(final G gFoo) {
        foo = gFoo;
        bar = 1;
        System.out.println(this.bar);

        G g = this;
        while (g.foo != null) {
            g = g.foo;
        }
    }
}