import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class B {
    public void f(@NotNull String p){}
    @NotNull
    public String nn(@Nullable String param) {
        return "";
    }
}
         
public class Y extends B {
    @NotNull @Nullable String s;
    public void f(String p){}
       
          
    public String nn(@NotNull String param) { 
        return "";
    }
    void p(@NotNull @Nullable String p2){}


    @Nullable int f;
    @NotNull void vf(){}
    void t(@NotNull double d){}
}
