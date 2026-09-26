import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

class Main {
    Map<String, Consumer<String>> map = new HashMap<>();

    void main() {
        map.put("default", switch (0) {
            default -> {
                if (true) yield _ -> {};
                else yield _ -> {};
            }
        });
    }
}
