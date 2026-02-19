class Clazz {
    public static void main(String[] args) {
        Child child = x -> System.out.println();
    }
}

interface I {
    void execute(int i);
}

interface Child extends I {
    default void execute(int i) {}
    void f(int i);
}