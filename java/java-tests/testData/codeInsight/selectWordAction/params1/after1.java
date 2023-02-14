public class Test {
  void foo() {
      findTargetElement(
        <caret><selection>REFERENCED_ELEMENT_ACCEPTED</selection>
        | SUPER_ACCEPTED,
        offset
      );
  }
}