import java.util.Date;

// "Fix all 'Unnecessary fully qualified name' problems in file" "true"
class FullyQualifiedName {

  void m(Object value) {
    value = ((Date) value).getTime();
  }
}