import org.jetbrains.annotations.Nullable;

class Test {
    void test(int x, float y){
        Integer code = newMethod(x);
        if (code == null) return;
        System.out.println(code);
    }

    private @Nullable Integer newMethod(int x) {
        int code;
        if (x == 22) return null;
        if (x > 0) {
            code = 1;
        } else {
            code = 42;
        }
        return code;
    }
}