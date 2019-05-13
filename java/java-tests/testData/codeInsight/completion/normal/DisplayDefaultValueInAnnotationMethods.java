@interface Anno {
  String myString() default "unknown";
  boolean myBool() default false;
}

@Anno(<caret>)
public class State {
  
}