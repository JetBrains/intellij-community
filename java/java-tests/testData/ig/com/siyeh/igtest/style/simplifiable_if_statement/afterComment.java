// "Replace 'if else' with '?:'" "INFORMATION"
class Test {
  
  Value myValue;
  
  interface Value {
    Object getEvaluationExpression(boolean b);
    String getName();
  }
  
  interface KeyedValue {}

  String foo(Value child){
      // Handling properties of the object
      // Handling ivar-s
      return child instanceof KeyedValue ? "(id)" : "(" + myValue.getEvaluationExpression(true) + ")->" + child.getName();
  }
}