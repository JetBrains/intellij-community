public class Class2 {
    void test(Object o) {
        if (o instanceof B && ((A)o).a() && o.<caret>
    }
}


interface A {
    boolean a();
}

interface B extends A {
    boolean b();
}