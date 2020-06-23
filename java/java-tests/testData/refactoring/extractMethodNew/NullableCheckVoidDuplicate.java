public class NullableCheckVoidDuplicate {
    void foo() {
        <selection>Object o = "";
        for (int i = 0; i < 5; i++) {
            if (i == 10) {
                o = null;
            }
        }
        if (o == null) {
            return;
        }</selection>
        System.out.println(o);
    }

    void bar() {
        Object o = "";
        for (int i = 0; i < 5; i++) {
            if (i == 10) {
                o = null;
            }
        }
        if (o == null) {
            return;
        }
        System.out.println(o);
    }
}