class Test23 {
    void m() throws Exception {
        try (FooContext fo<caret>o = new FooContext()) {
            try (BarContext ignored = new BarContext()) {
                fooBar();
            }
        }
    }

    private void fooBar() {

    }

    private class FooContext implements AutoCloseable {
        @Override
        public void close() throws Exception {

        }
    }

    private class BarContext implements AutoCloseable{
        @Override
        public void close() throws Exception {

        }
    }
}