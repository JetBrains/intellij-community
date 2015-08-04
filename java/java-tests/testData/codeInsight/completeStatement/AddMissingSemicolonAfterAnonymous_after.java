class Foo {
    void a() {
        Runnable r = new Runnable() {
            @Override
            public void run() {

            }
        };<caret>
    }
}