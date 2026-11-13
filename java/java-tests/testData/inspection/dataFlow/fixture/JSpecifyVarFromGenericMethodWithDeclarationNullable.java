import bar.NullableNick;
import lib.ResponseSpec;

@org.jspecify.annotations.NullMarked
class Scratch {
  @NullableNick String declarationNullableField;

  @NullableNick String declarationNullableMethod() {
    return null;
  }

  String notNullMethod() {
    return "";
  }

  void classLiteral(ResponseSpec spec) {
    var response = spec.body(String.class);
    System.out.println(response.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
  }

  void method() {
    var value = declarationNullableMethod();
    System.out.println(value.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
  }

  void field() {
    var value = declarationNullableField;
    System.out.println(value.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
  }

  void parameter(@NullableNick String parameter) {
    var value = parameter;
    System.out.println(value.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
  }

  void conditional(boolean flag) {
    var value = flag ? declarationNullableField : notNullMethod();
    System.out.println(value.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
  }

  void notNull() {
    var value = notNullMethod();
    System.out.println(value.length());
  }
}
