import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
    void test(){
        @Nullable String s = null;
        if (s == null) return;
        newMethod(s);
    }

    private void newMethod(@NotNull String s) {
        System.out.println(s);
    }
}