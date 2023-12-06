class Test2 {
    static void bar(Runnable r){}

    {
        bar((<caret>) -> {});
    }
}