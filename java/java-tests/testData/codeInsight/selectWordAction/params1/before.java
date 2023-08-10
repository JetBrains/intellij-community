public class Test {
  void foo() {
      findTargetElement(
        <caret>REFERENCED_ELEMENT_ACCEPTED
        | SUPER_ACCEPTED,
        offset
      );
  }
}