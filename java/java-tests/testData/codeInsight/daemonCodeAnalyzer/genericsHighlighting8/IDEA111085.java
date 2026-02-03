import java.io.*;
import java.util.List;

class Main {
    public <T> T foo(String str, Class<T> classOfT) {
        return null;
    }

    public <T> T foo(String str, Serializable typeOfT) {
        return null;
    }

    {
        asList(foo("", String[].class));
    }

    public static <T> List<T> asList(T... a) {
        return null;
    }
}
