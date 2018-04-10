// "Remove 'if' statement extracting side effects" "true"
import org.jetbrains.annotations.NotNull;
class X {
    public String getRole(Object parent) {
        if (parent instan<caret>ceof Foo && ((Foo)parent).getBar() == null) {
            return "a";
        }
        else {
        }
        return null;
    }
}

interface Foo {
    @NotNull
    Integer getBar();
}