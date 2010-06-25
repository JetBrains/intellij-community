// "Annotate method as @NotNull" "true"
import org.jetbrains.annotations.NotNull;

class X {
    @NotNull
    String annotateBase() {
        return "X";
    }
}
class Y extends X{
    String annotateBase() {
        return "Y";
    }
}
class Z extends Y {
    String annotateBase<caret>() { // trigger quick fix for inspection here
        return "Z";
    }
}
