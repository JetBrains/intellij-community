class Foo {
    void foo(String s) {
        Runnable r = () -> {
            String s2 = s.toString();
            Runnable r2 = () -> System.out.println(s<caret>2);
        };
    }
}
