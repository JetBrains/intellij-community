import java.util.Objects;

// "Convert record to class" "true-preview"
public final class TooWidePermissions //simple end comment {
{
    private final String key;

    public TooWidePermissions(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (TooWidePermissions) obj;
        return Objects.equals(this.key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "TooWidePermissions[" +
                "key=" + key + ']';
    }
}
}