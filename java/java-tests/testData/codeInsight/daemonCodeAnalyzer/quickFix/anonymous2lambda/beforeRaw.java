// "Replace with lambda" "false"
class DbTableBinder {

  public Binder build() {
    return new Bin<caret>der<DbTable>() {
      public void bind(A q, DbTable dbTable) {
        q.bind("name", dbTable.name);
      }
    };
  }
}

class DbTable {
  String name;
}

interface Binder <ArgType> {
  void bind(A<?> sqlStatement, ArgType argType);
}

interface A<P> {
  void bind(String s, String p);
}

