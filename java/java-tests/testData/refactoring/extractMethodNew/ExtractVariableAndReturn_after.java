import org.jetbrains.annotations.Nullable;

class Test {
    int test(int x){
        String out = newMethod(x);
        if (out == null) return 0;
        System.out.println(out);
        return -1;
    }

    private @Nullable String newMethod(int x) {
        String out = "out";
        if (x > 10) return null;
        if (x < 10) return null;
        return out;
    }
}
