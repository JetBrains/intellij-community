class Test {
    String test(Object[] args) {
        if (args != null) {
            return "";
        } else {
            throw newMethod();
        }
    }

    private RuntimeException newMethod() {
        return fail("No headers defined", null);
    }

    RuntimeException fail(String message, Object[] args) {
        return new RuntimeException(message);
    }
}
