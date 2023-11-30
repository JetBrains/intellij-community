// "Replace with 'switch' expression" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {
  int test(int x) {
    switc<caret>h (x) {
      case 1:return 2;
      case 2:return 4;
      case 3:return 6;
      default:
    }
    throw new IllegalArgumentException();
  }
}