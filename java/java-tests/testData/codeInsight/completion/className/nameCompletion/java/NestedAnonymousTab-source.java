class Bar {
    void v() {
        new Runnable() {
            @Override
            public void run() {
                new Fo<caret>o() {
                    @Override
                    public void run() {
                    }
                };

            }
        }.run();
    }
}

class Foo {
    Foo() {}
}