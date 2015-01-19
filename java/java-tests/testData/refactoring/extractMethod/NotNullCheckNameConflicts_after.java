import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X {
    @NotNull
    public X fun1(int x) {
        return this;
    }

    public X fun2(boolean b) {

        X x1 = newMethod(b);
        if (x1 != null) return x1;

        int x = 0;
        return null;
    }

    @Nullable
    private X newMethod(boolean b) {
        if (b) {
            int x = 1;
            return fun1(x);
        }
        return null;
    }


    void foo(int i, int j) {
        bar(i, j);
    }

    private void bar(int i, int j) {

    }
}
