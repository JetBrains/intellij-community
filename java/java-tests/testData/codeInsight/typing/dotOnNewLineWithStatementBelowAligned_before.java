public class Test {
    void foo(FooBuilder builder) {
        FooBuilder foo = builder.withA()
                <caret>
        System.out.println("Hello World");
    }
}