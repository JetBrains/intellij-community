// "Annotate overridden methods as @NotNull" "true"
import org.jetbrains.annotations.NotNull;

abstract class P2 {
    @NotNull<caret>
    String foo(@NotNull P p) {
        return "";
    }
}

class PPP extends P2 {
    @NotNull
    String foo(P p) {
        return super.foo(p);
    }
}
class PPP2 extends P2 {

    @NotNull
    String foo(P p) {
        return super.foo(p);
    }
}