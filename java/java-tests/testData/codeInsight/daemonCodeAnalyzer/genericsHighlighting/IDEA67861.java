abstract class C {
        <T> void foo(Object s){ }
        abstract String foo(String s);

    {
        this.<String>foo("").toLowerCase();
    }
}