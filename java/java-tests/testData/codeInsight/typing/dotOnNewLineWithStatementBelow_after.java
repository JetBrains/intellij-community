public class Test {
    void foo() {
        FooBuilder foo = new FooBuilder()
                .withA()
                .<caret>
        System.out.println("Hello World");
    }
}