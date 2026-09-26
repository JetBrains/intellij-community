public class Test {
    void foo(FooBuilder builder) {
        FooBuilder foo = builder
                .withA()
                  .withB()
                  .<caret>
        System.out.println("Hello World");
    }
}