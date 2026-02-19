class Test {
    void foo() {
        try {
            // This is comment"
            int i = 1;
        } catch (Exception e) {
            <caret><selection>throw new RuntimeException(e);</selection>
        }
    }
}