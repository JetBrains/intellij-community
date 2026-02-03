// "Unwrap 'if' statement" "true-preview"
class X {
    StringBuilder str = new StringBuilder();

    void test(@org.jetbrains.annotations.NotNull String x) {
        if(x != <caret>null) {
            String str = x.trim();
            System.out.println(str);
        }

        str.append("foo");
    }
}