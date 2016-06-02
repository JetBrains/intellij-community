import java.util.List;

public class Param<T> {
    private final List<T> y;

    public Param(List<T> y) {
        this.y = y;
    }

    public List<T> getY() {
        return y;
    }
}
