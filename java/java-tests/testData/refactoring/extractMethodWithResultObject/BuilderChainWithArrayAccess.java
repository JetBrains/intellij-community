class UseBuilder {
    void test(Builder builder, int[] arr) {
        <selection>builder.foo("xyz").bar(arr[0]).foo("abc");</selection>
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
