import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
    static class A {
        static class B {}
    }

    void test(){
        @Nullable A.B x = new A.B();
        if (x == null) return;
        newMethod(x);
    }

    private void newMethod(A.@NotNull B x) {
        System.out.println(x);
    }
}