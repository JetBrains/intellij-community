record MyRecord(String name, int id) {

  public MyRecord {
    validateMyFields();
  }

  private void validateMyFields() {
    if (this.name.<warning descr="Method invocation 'isEmpty' may produce 'NullPointerException'">isEmpty</warning>()) {
      throw new IllegalArgumentException();
    }
  }
}

class OrdinaryClassInitBefore {

  final String name;
  int id;

  public OrdinaryClassInitBefore() {
    name = "";
    validateMyFields();
  }

  private void validateMyFields() {
    if (this.name.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }
}

class OrdinaryClassInitAfter {

  final String name;
  int id;

  public OrdinaryClassInitAfter() {
    validateMyFields();
    name = "";
  }

  private void validateMyFields() {
    if (this.name.<warning descr="Method invocation 'isEmpty' may produce 'NullPointerException'">isEmpty</warning>()) {
      throw new IllegalArgumentException();
    }
  }
}

class OrdinaryClassNotNull {

  String name;
  int id;

  public OrdinaryClassNotNull() {
    validateMyFields();
    name = "";
  }

  private void validateMyFields() {
    if (this.name.<warning descr="Method invocation 'isEmpty' may produce 'NullPointerException'">isEmpty</warning>()) {
      throw new IllegalArgumentException();
    }
  }
}

class OrdinaryClassNotNullSeveralCalls {

  String name;
  int id;

  public OrdinaryClassNotNullSeveralCalls() {
    initName();
    validateMyFields();
    name = "";
  }

  private void initName() {
    name = "";
  }

  private void validateMyFields() {
    if (this.name.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }
}