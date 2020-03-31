package pack;

record MyRecord (String s) {
  MyRecord {
    String x = s();
  }
}