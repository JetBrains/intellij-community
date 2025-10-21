import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.FilenameFilter;

class B {
    public void f(@NotNull String p){}
    @NotNull
    public String nn(@Nullable String param) {
        return "";
    }
}
         
class Y extends B {
    <warning descr="Cannot annotate with both @NotNull and @Nullable">@NotNull</warning> <warning descr="Cannot annotate with both @Nullable and @NotNull">@Nullable</warning> String s;
    public void f(String <warning descr="Not annotated parameter overrides @NotNull parameter">p</warning>){}
       
          
    public String <warning descr="Not annotated method overrides method annotated with @NotNull">nn</warning>(<warning descr="Parameter annotated @NotNull must not override @Nullable parameter">@NotNull</warning> String param) { 
        return "";
    }
    void p(<warning descr="Cannot annotate with both @NotNull and @Nullable">@NotNull</warning> <warning descr="Cannot annotate with both @Nullable and @NotNull">@Nullable</warning> String p2){}


    <warning descr="Primitive type members cannot be annotated">@Nullable</warning> int f;
    <warning descr="Primitive type members cannot be annotated">@NotNull</warning> void vf(){}
    void t(<warning descr="Primitive type members cannot be annotated">@NotNull</warning> double d){}
}

@NotNullByDefault
class UnionTypeArgumentWithUseSite {
  interface Super<T extends @Nullable Object> {
    void t(T t);
    
    void t2(@Nullable T t);
  }

  interface Sub extends Super<Object> {
    @Override
    void t(Object t);

    @Override
    void t2(Object <warning descr="Parameter annotated @NotNullByDefault must not override @Nullable parameter">t</warning>);
  }
}
class MyFile extends File {
  public MyFile(@NotNull String pathname) {
    super(pathname);
  }

  // Test external annotations
  @Override
  public String @NotNull [] list(<warning descr="Parameter annotated @NotNull must not override @Nullable parameter">@NotNull</warning> FilenameFilter filter) {
    return super.list(filter);
  }
}
