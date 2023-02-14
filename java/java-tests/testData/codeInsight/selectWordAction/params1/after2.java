public class Test {
  void foo() {
      findTargetElement(
<selection>        <caret>REFERENCED_ELEMENT_ACCEPTED
</selection>        | SUPER_ACCEPTED,
        offset
      );
  }
}