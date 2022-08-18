class Foo {
  void foo(){
    int lineStart = document.getLineStartOffset<caret>(document.getLineNumber(offset));
  }
}