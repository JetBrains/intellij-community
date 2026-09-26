<warning descr="Unused import statement">import module my.source.moduleB;</warning>
<warning descr="Unused import statement">import module my.source.moduleC;/*unused*/</warning>
<warning descr="Unused import statement">import module my.source.moduleE;/*unused*/</warning>

import my.source.moduleB.*;/*unused*/ //on-fly
import my.source.moduleC.*;/*unused*/ //on-fly

final class Main {
  public static void main(String[] args) {
    new Sql();
    new Transaction();
  }
}
     