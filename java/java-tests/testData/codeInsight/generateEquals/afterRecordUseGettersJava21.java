
record Point(int x, int y, int z) {

    public final boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        if (!super.equals(o)) return false;

        final Point point = (Point) o;
        return x() == point.x() && y() == point.y() && z() == point.z();
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + x();
        result = 31 * result + y();
        result = 31 * result + z();
        return result;
    }
}