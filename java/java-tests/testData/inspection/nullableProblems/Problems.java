import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class B {
    public void f(@NotNull String p){}
    @NotNull
    public String nn(@Nullable String param) {
        return "";
    }
}
         
class Y extends B {
    <warning descr="Cannot annotate with both @Nullable and @NotNull">@NotNull</warning> <warning descr="Cannot annotate with both @Nullable and @NotNull">@Nullable</warning> String s;
    public void f(String <warning descr="Not annotated parameter overrides @NotNull parameter">p</warning>){}
       
          
    public String <warning descr="Not annotated method overrides method annotated with @NotNull">nn</warning>(<warning descr="Parameter annotated @NotNull must not override @Nullable parameter">@NotNull</warning> String param) { 
        return "";
    }
    void p(<warning descr="Cannot annotate with both @Nullable and @NotNull">@NotNull</warning> <warning descr="Cannot annotate with both @Nullable and @NotNull">@Nullable</warning> String p2){}


    <warning descr="Primitive type members cannot be annotated">@Nullable</warning> int f;
    <warning descr="Primitive type members cannot be annotated">@NotNull</warning> void vf(){}
    void t(<warning descr="Primitive type members cannot be annotated">@NotNull</warning> double d){}
}
