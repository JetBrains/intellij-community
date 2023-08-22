public class CommentsInConditional {
  boolean b = (<caret>true && false/**/ ? /**/"shark"/*1*/ : /*3*/"nado"/*2*/);
}