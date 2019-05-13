import org.jetbrains.annotations.NotNull;

class X {
    void foo(@NotNull Object o) { }
}

class Y extends X {
    @Override
    void foo(Object o) {
        if (o == null) {
            System.out.println("null is allowed");
        }
    }
}