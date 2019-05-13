import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Demo {
    public static void main(String[] args) {
        Function<? super String, ? extends List<String>> mappingFunction = key -> new ArrayList<>();
    }

    interface Function<T, R> {
      public R apply(T t);
    }
}
