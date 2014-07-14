@FunctionalInterface
interface Base {
    default void foo() {
        System.out.println("Hi there.");
    }
}

class Child implements Base {
    public abstract void bar();
}
