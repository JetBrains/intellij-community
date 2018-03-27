// "Remove 'if' statement extracting side effects" "true"
import org.jetbrains.annotations.NotNull;
class X {
    public String getRole(Object parent) {
        if (parent instanceof Foo) {
            ((Foo) parent).getBar();
        }
        return null;
    }
}

interface Foo {
    @NotNull
    Integer getBar();
}