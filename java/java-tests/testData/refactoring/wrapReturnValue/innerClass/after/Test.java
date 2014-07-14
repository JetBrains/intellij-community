class Test {
    Wrapper foo() {
        return new Wrapper("");
    }

    void bar() {
        String s = foo().getValue();
    }

    public class Wrapper {
        private final String value;

        public Wrapper(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}