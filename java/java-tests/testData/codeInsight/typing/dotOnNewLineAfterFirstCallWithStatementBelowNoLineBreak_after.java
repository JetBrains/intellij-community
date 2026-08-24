public class Test {
    void foo(FooBuilder builder) {
        FooBuilder foo = new FooBuilder()
                .<caret>
        System.out.println("Hello World");
    }
}