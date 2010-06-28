// "Make 'victim' return 'boolean'" "true"
public class External {
    void m1() {
        if (new Out().victim(null)) {
            System.out.println("Something");
        }
    }
}

class Out {
    public boolean victim(final Collection coll) {
        if (System.currentTimeMillis() > 2321321L) {
            final boolean value;

            return value;
        }

        final boolean inTheMiddle = false;
        for (Object o : coll) {
            return inTheMiddle;
        }

        for (Iterator iterator = coll.iterator(); iterator.hasNext();) {
            Object o = (Object) iterator.next();
            boolean insideFor = false;

            return inTheMiddle;
        }
        return <caret><selection>inTheMiddle</selection>;
    }
}
