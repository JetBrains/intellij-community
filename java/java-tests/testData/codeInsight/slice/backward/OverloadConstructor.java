import java.util.*;

public class  Auto {
    public Auto(ArrayList w, Runnable <caret>i) {
        this(w, null,null);
    }
    public Auto(ArrayList w, Runnable  i,Runnable o) {
        o.hashCode();
    }

    {

        new Auto(null,null,null);
        new Auto(null,<flown1>null);
    }

}
