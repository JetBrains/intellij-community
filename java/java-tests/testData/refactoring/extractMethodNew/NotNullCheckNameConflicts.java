import org.jetbrains.annotations.NotNull;

class X {
    @NotNull
    public X fun1(int x) {
        return this;
    }

    public X fun2(boolean b) {

        <selection>if (b) {
            int x = 1;
            return fun1(x);
        }</selection>

        int x = 0;
        return null;
    }




    void foo(int i, int j) {
        bar(i, j);
    }

    private void bar(int i, int j) {

    }
}
