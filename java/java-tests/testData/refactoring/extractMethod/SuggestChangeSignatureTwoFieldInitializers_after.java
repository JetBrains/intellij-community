import org.jetbrains.annotations.NotNull;

public class C {
    String f1 = newMethod("a");

    @NotNull
    private String newMethod(String a) {
        return a + "b";
    }

    String f2 = newMethod("c");
}