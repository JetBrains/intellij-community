class Test {
    String foo() {
        I i = () -> {
            return 1;
        };
        return "";
    }
}

interface I {
    Integer bar();
}