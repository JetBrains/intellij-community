// "Replace 'if else' with '?:'" "INFORMATION"
class Test {
  
  Value myValue;
  
  interface Value {
    Object getEvaluationExpression(boolean b);
    String getName();
  }
  
  interface KeyedValue {}

  String foo(Value child){
    i<caret>f (child instanceof KeyedValue) {
      // Handling properties of the object
      return "(id)";
    }
    else {
      // Handling ivar-s
      return "(" + myValue.getEvaluationExpression(true) + ")->" + child.getName();
    }
  }
}