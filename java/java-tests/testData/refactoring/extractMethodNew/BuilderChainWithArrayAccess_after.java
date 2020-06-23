class UseBuilder {
    void test(Builder builder, int[] arr) {
        newMethod(builder, arr[0]);
    }

    private void newMethod(Builder builder, int x) {
        builder.foo("xyz").bar(x).foo("abc");
    }

    static class Builder {
        Builder foo(String s) {
            return this;
        }

        Builder bar(int x) {
            return this;
        }
    }
}
