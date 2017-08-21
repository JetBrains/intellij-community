// "Annotate method 'dontAnnotateBase' as @NotNull" "true"
import org.jetbrains.annotations.NotNull;

class X {
    @NotNull
    String dontAnnotateBase() {
        return "X";
    }
}
class Y extends X{
    String dontAnnotateBase() {
        return "Y";
    }
}
class Z extends Y {
    @NotNull
    String dontAnnotateBase<caret>() { // trigger quick fix for inspection here
        return "Z";
    }
}
