import org.jetbrains.annotations.*;

class Test {
  void test(@Nullable Integer i) {
    switch (i) {
        case null -> {}<caret>
    }
  }
}