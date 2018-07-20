
import java.util.Comparator;

class Foo implements Comparable<Foo> {

    public String getName() {
        return "";
    }

    @Override
    public int compareTo(final Foo o) {
        return <selection>Comparator.comparing(Foo::getName, String.CASE_INSENSITIVE_ORDER)</selection>.compare(this, o);
    }
}