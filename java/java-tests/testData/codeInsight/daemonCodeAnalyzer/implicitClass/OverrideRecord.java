interface MyInterface {
  String value();
}


record MyRecord(String value) implements MyInterface {}

void main() {
  System.out.println(new MyRecord("hello").value());
}