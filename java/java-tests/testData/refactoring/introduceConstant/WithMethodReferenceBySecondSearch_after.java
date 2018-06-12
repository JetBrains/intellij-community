
import java.util.Comparator;

class Foo implements Comparable<Foo> {

    public static final Comparator<Foo> xxx = Comparator.comparing(Foo::getName, String.CASE_INSENSITIVE_ORDER);

    public String getName() {
        return "";
    }

    @Override
    public int compareTo(final Foo o) {
        return xxx.compare(this, o);
    }
}