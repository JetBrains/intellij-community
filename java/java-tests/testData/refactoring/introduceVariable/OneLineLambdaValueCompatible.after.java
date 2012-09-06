class Foo {  
    void test() {
        Comparable<String> java = o -> {
            int c = o.length();
            return c;
        };
    }
}