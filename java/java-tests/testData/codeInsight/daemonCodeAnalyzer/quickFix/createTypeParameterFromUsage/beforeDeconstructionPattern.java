// "Create type parameter 'Rec'" "false"

public class UnresolvedPattern {
  void foo(Object o) {
    switch (o) {
      case <caret>Rec(int i) -> {}
    }
  }
}