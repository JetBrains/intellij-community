@FunctionalInterface
interface Base {
    default void foo() {
        System.out.println("Hi there.");
    }
    void ba<caret>r();
}

class Child implements Base {
}
