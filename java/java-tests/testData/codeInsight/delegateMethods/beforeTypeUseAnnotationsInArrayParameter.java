import org.jetbrains.annotations.NotNull;

class Test1 {
    void m(@NotNull String... strings) { }
}

class Test2 {
    private Test1 t;

    <caret>
}