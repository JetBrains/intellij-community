class UseBuilder {
    void test(Builder builder, int[] arr) {
        newMethod(builder, arr[0]).foo("abc");
    }

    private Builder newMethod(Builder builder, int x) {
        return builder.foo("xyz").bar(x);
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
