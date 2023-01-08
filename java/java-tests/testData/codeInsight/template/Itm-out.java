import java.util.*;

class BarGoo {}

class Foo {
    {
        Map<String, BarGoo> goos;
        for (Map.Entry<String, BarGoo> stringBarGooEntry : goos.entrySet()) {
            String key = stringBarGooEntry.getKey();
            BarGoo value = stringBarGooEntry.getValue();
            <caret>
        }
    }
}