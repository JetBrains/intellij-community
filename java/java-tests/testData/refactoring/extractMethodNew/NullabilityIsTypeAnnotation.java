import org.jetbrains.annotations.Nullable;

class Test {
    static class A {
        static class B {}
    }

    void test(){
        @Nullable A.B x = new A.B();
        if (x == null) return;
        <selection>System.out.println(x);</selection>
    }
}