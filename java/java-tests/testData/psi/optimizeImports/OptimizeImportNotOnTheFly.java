import java.util.List;
import java.util.ArrayList;
<warning descr="Unused import statement">import java.util.Map;</warning>
<warning descr="Unused import statement">import java.util.Set;</warning>

class MissortedImports{
  public static void main(String[] <warning descr="Parameter 'args' is never used">args</warning>) {
    List<String> a = new ArrayList<>();
    System.out.println(a);
  }
}