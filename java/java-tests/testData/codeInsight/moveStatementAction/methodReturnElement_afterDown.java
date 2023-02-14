public class FF {
    @NotNull
    public Object bar () { //<--- MOVE THIS UP
        int
                c;
    }
    {
      int fff;
    }
    @Nullable
    public
    Object
    foo () { //xxxx<--- MOVE THIS UP
        return null;
    }
    void ff(){}
}

