// "Migrate to enhanced switch with rules" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {

  int test(int y) {
    return swi<caret>tch (y) { //some comments3
      case 1:
        yield 1; //some comments2
      case 2:
        yield 3; /*some comments1*/
      default:
        yield 5; //some comments
    };
  }
}