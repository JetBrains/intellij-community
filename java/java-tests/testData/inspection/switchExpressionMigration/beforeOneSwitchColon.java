// "Migrate to enhanced switch with rules" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {

  int test(int y) {
    return swi<caret>tch (y) {
      case 1:
        System.out.println("1");
        yield 1;
      case 2:
        yield 3;
      case 3:
        throw new IllegalArgumentException();
      default:
        System.out.println();
        yield 5;
    };
  }
}