@FunctionalInterface
interface Base {
    default void foo() {
        System.out.println("Hi there.");
    }
}

abstract class Child implements Base {
    public abstract void bar();
}
