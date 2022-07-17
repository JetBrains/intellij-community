// "Convert canonical constructor to compact form" "true"
import java.lang.annotation.*;

record Rec(@Anno @Anno2 int x, int y) {
  public Rec(@Anno int x<caret>, int y) {
    if (x < 0) throw new IllegalArgumentException();
    this.x = x;
    this.y = y;
  }
}

@interface Anno {}
@Target({ElementType.FIELD})
@interface Anno2 {}