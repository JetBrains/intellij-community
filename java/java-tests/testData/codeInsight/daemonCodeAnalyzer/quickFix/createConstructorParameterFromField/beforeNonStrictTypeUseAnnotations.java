// "Add constructor parameter" "true"
public class A {
  @I
  private int <caret>field;
}
@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE, java.lang.annotation.ElementType.FIELD})
@interface I {}