import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class X  {
    private final static String CON = "";
    String <caret>l;
    private String d;

    public X(String g) {
        set("xxx");
        set(g);
        fs("oo", this);

        String o = (String)((null));
        set(o);

        set(new String());
        set(nn());
        set(CON);
        String nn = g == null ? CON : g;
        set(nn);

        String other = g == "" ? CON : g;
        set(other);

        g.hashCode(); // g derefernced before use, must be NN
        set(g);

        set(nu());
        set(hz());
    }

    @NotNull
    String nn() {
        return "";
    }
    @Nullable
    String nu() {
        return "";
    }
    String hz() {
        return d;
    }
    void fs(@Nullable String t, X x)
    {
        x.set(t);

        x.set(t == null ? "null" : t);
    }

    void set(String d) {
        l = d;
    }
    void setFromNN(@NotNull String d) {
        l = d;
    }
}
