// "Add constructor parameter" "true"
public class A {
  @I
  private int field;

    public A(@I int field) {
        this.field = field;
    }
}
@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE})
@interface I {}