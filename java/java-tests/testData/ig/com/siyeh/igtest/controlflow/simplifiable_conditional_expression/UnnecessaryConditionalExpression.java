class UnnecessaryConditionalExpression {

  String str1(boolean value) {
    return <warning descr="'value ? \"true\" : \"false\"' can be simplified to 'java.lang.Boolean.toString(value)'">value</warning> ? "true" : "false";
  }

  String str2(boolean value) {
    return <warning descr="'value ? \"false\" : \"true\"' can be simplified to 'java.lang.Boolean.toString(!value)'">value</warning> ? "false" : "true";
  }

  String str3(Boolean value) {
    return <warning descr="'value ? \"true\" : \"false\"' can be simplified to 'value.toString()'">value</warning> ? "true" : "false";
  }

  String str4(Boolean value) {
    return <warning descr="'value ? \"false\" : \"true\"' can be simplified to 'java.lang.Boolean.toString(!value)'">value</warning> ? "false" : "true";
  }

  String str5(int a, int b) {
    return <warning descr="'a > b ? \"true\" : \"false\"' can be simplified to 'java.lang.Boolean.toString(a > b)'">a > b</warning> ? "true" : "false";
  }

  String str6(boolean value) {
    return value ? "true" : "yes";
  }

  String str7(int num) {
    return <warning descr="'num > 0 //comment
           ? \"true\" : \"false\"' can be simplified to 'java.lang.Boolean.toString(num > 0)'">num > 0</warning> //comment
           ? "true" : "false";
  }

  String str8(int num) {
    return <warning descr="'num //comment
           > 0 ? \"true\" : \"false\"' can be simplified to 'java.lang.Boolean.toString(num //comment
           > 0)'">num //comment
           > 0</warning> ? "true" : "false";
  }

  String str9(int num) {
    // We don't want to be "too smart". Let's not suggest a fix in this case.
    final String finalFalseStr = "false";
    return num > 0 ? "true" : finalFalseStr;
  }

  void one(boolean condition) {
    final boolean a = <warning descr="'condition ? true : false' can be simplified to 'condition'">condition</warning> ? true : false;
    final boolean b = <warning descr="'condition ? false : true' can be simplified to '!condition'">condition</warning> ? false : true;
  }

  int two(int i) {
    return <warning descr="'i == 0 ? 0 : i' can be simplified to 'i'">i == 0</warning> ? 0 : i;
  }

  Object three(Object o) {
    return <warning descr="'o != null ? o : null' can be simplified to 'o'">o != null</warning> ? o : null;
  }

  int four(int a, int b) {
    return <warning descr="'a == b ? a : b' can be simplified to 'b'">a == b</warning> ? a : b;
  }
  
  boolean or(int a, int b) {
    return <warning descr="'a > b ? true : b == 5' can be simplified to 'a > b || b == 5'">a > b</warning> ? true : b == 5;
  }
  
  boolean and(int a, int b) {
    return <warning descr="'a > b ? false : b == 5' can be simplified to 'a<=b && b == 5'">a > b</warning> ? false : b == 5;
  }
  
  boolean cond(int a, int b, int c) {
    return <warning descr="'a > 0 ? b < c : b >= c' can be simplified to '(a > 0) == (b < c)'">a > 0</warning> ? b < c : b >= c;
  }

  // IDEA-267511
  void confusing() {
    Boolean x = Math.random() > 0.5 ? true : Math.random() > 0.5 ? false : null;
  }
}

class InsideLambdaInOverloadedMethod {
  Boolean myField;
  void m(I<Boolean> i) {}
  void m(IVoid i) {}

  Boolean get() {return myField;}

  {
    m(() -> <warning descr="'get() ? false : true' can be simplified to '!get()'">get()</warning> ? false : true);
    m(() -> get() ? true : false);
  }
}

interface I<T> {
  T f();
}

interface IVoid extends I<Void>{
  void foo();

  @Override
  default Void f() {
    return null;
  }
}
