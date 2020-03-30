import org.jetbrains.annotations.Nullable;

public class InferredNotNullInReturnStatementDuplicate {
    void foo(java.util.List l) {
        for (Object o : l) {

            Object x = newMethod(o);
            if (x == null) continue;
            System.out.println(x);
        }
    }

    @Nullable
    private Object newMethod(Object o) {
        if (o == null) return null;
        Object x = bar(o);
        return x;
    }

    void bar(java.util.List l) {
        for (Object o : l) {
            Object x = newMethod(o);
            if (x == null) continue;
            System.out.println(x);
        }
    }

    private String bar(Object o) {return "";}
}
