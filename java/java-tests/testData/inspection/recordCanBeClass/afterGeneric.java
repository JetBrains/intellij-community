import java.util.Objects;

// "Convert record to class" "true-preview"
final class R<T> {
    private final T t;

    R(T t) {
        this.t = t;
    }

    public T t() {
        return t;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (R) obj;
        return Objects.equals(this.t, that.t);
    }

    @Override
    public int hashCode() {
        return Objects.hash(t);
    }

    @Override
    public String toString() {
        return "R[" +
                "t=" + t + ']';
    }
}