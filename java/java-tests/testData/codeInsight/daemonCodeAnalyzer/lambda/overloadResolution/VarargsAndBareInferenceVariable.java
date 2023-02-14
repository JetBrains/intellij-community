class Main {
    {
        bar(ge<caret>t());
    }

    static void bar(Object... o) {}
    static <T> T get() {
        return (T) null;
    }
}