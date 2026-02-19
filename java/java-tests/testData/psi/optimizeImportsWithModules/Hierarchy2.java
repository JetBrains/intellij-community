import module my.source.moduleB;
<warning descr="Unused import statement">import module my.source.moduleC;</warning>
<warning descr="Unused import statement">import module my.source.moduleD;</warning>

final class Main {
  public static void main(String[] args) {
    new Sql();
    new Transaction();
  }
}