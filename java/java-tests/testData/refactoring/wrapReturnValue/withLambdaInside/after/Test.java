class Test {
    Wrapper foo() {
        I i = () -> {
            return 1;
        };
        return new Wrapper("");
    }
}

interface I {
    Integer bar();
}