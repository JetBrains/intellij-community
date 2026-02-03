import org.jetbrains.annotations.Nullable;

class Test {
    void test(){
        @Nullable String s = null;
        if (s == null) return;
        <selection>System.out.println(s);</selection>
    }
}