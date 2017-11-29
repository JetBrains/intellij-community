import java.util.*;
class Foo {
    public final ArrayList<String> l = new ArrayList<>();

    {
        List<String> l = this.l;
    }
}