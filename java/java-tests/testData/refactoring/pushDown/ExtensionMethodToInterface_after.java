interface Base {
}

interface I2 extends Base {
    default void foo() {
        System.out.println("Hi there.");
    }
}