public class FF {
    @NotNull
    public Object bar () { //<--- MOVE THIS UP
        int
                c;
    }
    @Nullable
    public
    Object<caret>
            foo () { //xxxx<--- MOVE THIS UP
        return null;
    }
    {
      int fff;
    }
    void ff(){}
}

