import module my.source.moduleB;
<warning descr="Unused import statement">import module my.source.moduleC;/*unused*/</warning>
<warning descr="Unused import statement">import module my.source.moduleE;/*unused*/</warning>

final class Main {
  public static void main(String[] args) {
    new Sql();
    new Transaction();
  }
}
