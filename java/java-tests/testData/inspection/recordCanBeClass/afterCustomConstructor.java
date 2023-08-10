import java.util.Objects;

// "Convert record to class" "true-preview"
final class Range {
    static final Range ZERO = new Range(0);
    private final int x;
    private final int y;

    Range(int x, int y) {
        this.x = x;
        this.y = y;
    }

    Range(int x) {
        this(x, x);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Range) obj;
        return this.x == that.x &&
                this.y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Range[" +
                "x=" + x + ", " +
                "y=" + y + ']';
    }

}