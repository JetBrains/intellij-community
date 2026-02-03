public class In {

    void fool() {
        final boolean delete = narr();
        new Runnable() {
            public void run() {
                new Runnable() {
                    public void run() {
                        if (del<caret>ete) {

                        }
                    }
                }.run();
            }
        };
    }

    boolean narr() {
        return false;
    }
}
