// "Replace with lambda" "true"
class DbTableBinder {

  public Binder build() {
    return (Binder<DbTable>) (q, dbTable) -> q.bind("name", dbTable.name);
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

