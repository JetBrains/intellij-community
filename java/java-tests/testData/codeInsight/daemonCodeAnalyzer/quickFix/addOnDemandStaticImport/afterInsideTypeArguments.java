// "Add on demand static import for 'java.util.Map'" "true"

import java.util.List;
import java.util.Map;

import static java.util.Map.*;

class Foo {
    {
        List<Entry> l;
    }
}