
class Main {

    public interface Entity<T> {
    }

    public interface HasOrder {

    }

    public static <T extends Entity<K> & HasOrder, K> void beforeRemove(T item) {

    }

    public static void main(String[] args) {
        Object o = null;
        beforeRemove( (Entity & HasOrder) o);
    }
}