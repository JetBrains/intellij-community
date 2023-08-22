import org.jetbrains.annotations.*;

class Test {
  void test(@Nullable Integer i) {
    switch ((i)) {
      case 1:
        System.out.println();
        break;
        case null:
            break;
    }
  }
}