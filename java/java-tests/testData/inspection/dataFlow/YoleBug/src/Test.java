import org.jetbrains.annotations.*;
public class Test {
    @NotNull
    public Object foo() {
        return new Object();
    }

    public void qqq() {
        int c = foo() != null ? foo().hashCode() : 0;
    }
}
