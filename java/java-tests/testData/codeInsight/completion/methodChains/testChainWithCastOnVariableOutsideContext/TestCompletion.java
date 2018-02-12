interface Editor {}
interface EditorEx extends Editor {
  MarkupModelEx getMarkupModel();
}
interface MarkupModelEx {}

class Test {
  void m() {
    MarkupModelEx m = <caret>
  }
}