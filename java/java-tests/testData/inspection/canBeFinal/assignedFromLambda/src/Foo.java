abstract class Foo {
    private String foo;

    public A(String foo) {
        this.foo = foo;
        Runnable r = () -> {this.foo = "";};
    }
}