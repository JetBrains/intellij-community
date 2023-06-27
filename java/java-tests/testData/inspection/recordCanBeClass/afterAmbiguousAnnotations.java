// "Convert record to class" "true-preview"
import java.lang.annotation.*;
import java.util.Objects;

@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.TYPE_USE})
@interface Anno {}

final class R {
    private final @Anno int f;

    R(@Anno int f) {
        this.f = f;
    }

    public @Anno int f() {
        return f;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (R) obj;
        return this.f == that.f;
    }

    @Override
    public int hashCode() {
        return Objects.hash(f);
    }

    @Override
    public String toString() {
        return "R[" +
                "f=" + f + ']';
    }
}
