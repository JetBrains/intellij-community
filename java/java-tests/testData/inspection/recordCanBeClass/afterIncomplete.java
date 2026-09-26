import java.util.Objects;

// "Convert record to class" "true-preview"
final class Rec {
    private final int x;
    private final int y;

    Rec(int x, int y {
        this.x = x;
        this.y = y;
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
        var that = (Rec) obj;
        return this.x == that.x &&
                this.y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Rec[" +
                "x=" + x + ", " +
                "y=" + y + ']';
    }
}