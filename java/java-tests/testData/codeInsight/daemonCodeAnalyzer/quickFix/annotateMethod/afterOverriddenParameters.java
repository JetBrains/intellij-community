// "Annotate overriding method parameters as '@NotNull'" "true"
import org.jetbrains.annotations.NotNull;

abstract class P2 {
    @NotNull
    String foo(@NotNull<caret> P p) {
        return "";
    }
}

class PPP extends P2 {
    String foo(@NotNull P p) {
        return super.foo(p);
    }
}
class PPP2 extends P2 {

    String foo(@NotNull P p) {
        return super.foo(p);
    }
}