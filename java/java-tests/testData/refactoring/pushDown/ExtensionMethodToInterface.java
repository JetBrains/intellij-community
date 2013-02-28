interface Base {
    default void foo<caret>() {
        System.out.println("Hi there.");
    }
}

interface I2 extends Base {
}