class QualifyWithThis1 {
  String[] values = new String[10];
  void foo() {
    int size = values.length;
    String[] values = new String[10]);  // Let's hide field "values" with local variable

    for<caret> (int i = 0; i < size; i++) {
      String value = this.values[i];
    }
  }
}