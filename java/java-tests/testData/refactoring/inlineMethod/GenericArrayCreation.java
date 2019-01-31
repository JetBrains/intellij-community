
import java.util.Optional;

class Test
{
    void x() {
        Optional<String>[] os = arr<caret>ay(Optional.empty());
    }

    public static <T> T[] array(T... values) {
        return values;
    }
}