// "Migrate to enhanced switch with rules" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {

  int test(int y) {
    return switch (y) { //some comments3
        case 1 -> 1; //some comments2
        case 2 -> 3; /*some comments1*/
        default -> 5; //some comments
    };
  }
}