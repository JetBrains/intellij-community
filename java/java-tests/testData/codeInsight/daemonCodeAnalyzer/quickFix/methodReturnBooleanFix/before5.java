// "Make 'victim' return 'boolean'" "true"
public class External {
    void m1() {
        if (new Out().<caret>victim(null)) {
            System.out.println("Something");
        }
    }
}

class Out {
    public void victim(final Collection coll) {
        if (System.currentTimeMillis() > 2321321L) {
            final boolean value;

            return;
        }

        final boolean inTheMiddle = false;
        for (Object o : coll) {
            return;
        }

        for (Iterator iterator = coll.iterator(); iterator.hasNext();) {
            Object o = (Object) iterator.next();
            boolean insideFor = false;

            return;
        }
    }
}
