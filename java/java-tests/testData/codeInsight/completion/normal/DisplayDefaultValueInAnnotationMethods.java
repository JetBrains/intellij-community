@interface Anno {
  String myString() default "unknown";
  int myInt() default 42;
}

@Anno(<caret>)
public class State {
  
}