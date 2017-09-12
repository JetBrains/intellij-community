interface Editor {}
interface EditorEx extends Editor {
  MarkupModelEx getMarkupModel();
}
interface MarkupModelEx {}

class Test {
  void m(Editor editor) {
    ((EditorEx)editor).getMarkupModel();
    ((EditorEx)editor).getMarkupModel();
    ((EditorEx)editor).getMarkupModel();
    ((EditorEx)editor).getMarkupModel();
    ((EditorEx)editor).getMarkupModel();
    ((EditorEx)editor).getMarkupModel();
    ((EditorEx)editor).getMarkupModel();
    ((EditorEx)editor).getMarkupModel();
    ((EditorEx)editor).getMarkupModel();
  }
}