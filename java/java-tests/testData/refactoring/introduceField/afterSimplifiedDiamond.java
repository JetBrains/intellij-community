import java.util.*;
class Foo {
    public final ArrayList<String> strings = new ArrayList<>();

    {
        List<String> l = strings;
    }
}