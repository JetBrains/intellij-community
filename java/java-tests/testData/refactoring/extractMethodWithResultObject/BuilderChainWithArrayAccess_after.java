class UseBuilder {
    void test(Builder builder, int[] arr) {
        NewMethodResult x = newMethod(builder, arr);
    }

    NewMethodResult newMethod(Builder builder, int[] arr) {
        builder.foo("xyz").bar(arr[0]).foo("abc");
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
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
