// "Convert canonical constructor to compact form" "true"
import java.lang.annotation.*;

record Rec(@Anno @Anno2 int x, int y) {
    public Rec {
        if (x < 0) throw new IllegalArgumentException();
    }
}

@interface Anno {}
@Target({ElementType.FIELD})
@interface Anno2 {}