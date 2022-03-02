class Test {
    void foo () throws Exception {
    }

    void bar () {
        try {
            foo();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
