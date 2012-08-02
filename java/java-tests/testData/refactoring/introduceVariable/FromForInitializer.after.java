import java.util.*;

public class SomeClass {

    static List foo(){return null;}

    public static void main(String[] args) {
        final List list = foo();
        for (Iterator it = list.listIterator(list.size()); it.hasNext(); ) {

        }
    }
}