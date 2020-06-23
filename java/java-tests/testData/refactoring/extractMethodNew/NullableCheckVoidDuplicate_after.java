import org.jetbrains.annotations.Nullable;

public class NullableCheckVoidDuplicate {
    void foo() {
        Object o = newMethod();
        if (o == null) return;
        System.out.println(o);
    }

    @Nullable
    private Object newMethod() {
        Object o = "";
        for (int i = 0; i < 5; i++) {
            if (i == 10) {
                o = null;
            }
        }
        if (o == null) {
            return null;
        }
        return o;
    }

    void bar() {
        Object o = newMethod();
        if (o == null) return;
        System.out.println(o);
    }
}