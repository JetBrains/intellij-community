// "Create missing branches: 'Scratch.X', and 'Scratch.Parent.X'" "true"
class Scratch {
  sealed interface Parent {
    record X() implements Parent {}
  }
  record X() implements Parent {}
  record Y() implements Parent {}

  void test(Parent parent) {
    switch (parent<caret>) {
      case Y y -> {}
    }
  }
}