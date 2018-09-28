class X {

    Column[] c = new Column[]{
      new RepositoryItem20Col<caret>umnBase() {
          void foo() {
          }
      }
    };

    private static abstract class RepositoryItem20ColumnBase extends Column { }
    static class Column { }
}
