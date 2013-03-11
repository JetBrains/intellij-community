interface Base {
    default void foo<caret>() {
        System.out.println("Hi there.");
    }
}

class C implements Base {
}