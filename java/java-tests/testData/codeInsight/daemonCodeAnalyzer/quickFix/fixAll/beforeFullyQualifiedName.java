// "Fix all 'Unnecessary fully qualified name' problems in file" "true"
class FullyQualifiedName {

  void m(Object value) {
    value = ((java.<caret>util.Date) value).getTime();
  }
}