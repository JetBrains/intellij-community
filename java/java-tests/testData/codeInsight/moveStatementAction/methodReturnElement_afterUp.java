public class FF {
    @Nullable
    public
    Object
    foo () { //xxxx<--- MOVE THIS UP
        return null;
    }
    @NotNull
    public Object bar () { //<--- MOVE THIS UP
        int
                c;
    }
    {
      int fff;
    }
    void ff(){}
}

