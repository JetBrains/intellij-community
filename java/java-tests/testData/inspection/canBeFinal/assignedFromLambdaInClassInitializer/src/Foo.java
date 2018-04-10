abstract class Foo {
    private String foo = "1";
    {
        Runnable r = () -> {this.foo = "";};
    }
}