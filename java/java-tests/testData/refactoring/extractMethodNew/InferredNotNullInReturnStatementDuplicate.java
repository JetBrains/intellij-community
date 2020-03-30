public class InferredNotNullInReturnStatementDuplicate {
    void foo(java.util.List l) {
        for (Object o : l) {
            <selection>
            if (o == null) continue;
            Object x = bar(o);</selection>
            System.out.println(x);
        }
    }

    void bar(java.util.List l) {
        for (Object o : l) {
            if (o == null) continue;
            Object x = bar(o);
            System.out.println(x);
        }
    }

    private String bar(Object o) {return "";}
}
