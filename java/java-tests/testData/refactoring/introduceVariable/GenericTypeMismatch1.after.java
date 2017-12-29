import java.util.List;

class GenericTypeMismatch {
    interface Key<T> {}

    static final Key<String> KEY = new Key<String>() {};

    <T> java.util.List<T> getByKey(Key<T> key) {
        return null;
    }

    void test() {
        final List<String> i = getByKey(KEY);
        if(i)
        {

        }
    }
}
