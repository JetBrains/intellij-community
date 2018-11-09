import org.jetbrains.annotations.*;
class Test {
    @NotNull
    public Object foo() {
        return new Object();
    }

    public void qqq() {
        int c = <warning descr="Condition 'foo() != null' is always 'true'">foo() != null</warning> ? foo().hashCode() : 0;
    }
}
