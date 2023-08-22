public class FF {
    @Nullable
    public Object bar () { //<--- MOVE THIS UP
        int
                c;

        @Nullable
        int
                g;
        Runnable runnable = new <caret>Runnable() {
            public void run() {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        return null;
    }

    @Nullable
    public
    Object
            foo () { //xxxx<--- MOVE THIS UP
        return null;
    }

}
