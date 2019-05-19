class Bar {
    void v() {
        new Runnable() {
            @Override
            public void run() {
                new Foo()<caret> {
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