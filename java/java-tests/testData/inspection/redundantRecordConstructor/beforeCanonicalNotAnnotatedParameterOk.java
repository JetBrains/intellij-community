// "Convert canonical constructor to compact form" "true" 
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

record Rec(@Anno int x, int y) {
  public Rec(int x<caret>, int y) {
    this.x = y;
    this.y = y;
  }
}

@Target(ElementType.FIELD)
@interface Anno {}