import org.jetbrains.annotations.NotNull;

public class Foo {
    static Foo
            f1 = new Foo(){
                public String toString() {
                    return newMethod();
                }
            },
            f2 = new Foo(){};

    private static @NotNull String newMethod() {
        return "a" + "b";
    }

}