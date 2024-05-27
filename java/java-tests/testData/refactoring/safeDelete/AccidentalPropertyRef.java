class AccidentalPropertyRef {
  // mockjdk-21 contains a file java/time/chrono/hijrah-config-Hijrah-umalqura_islamic-umalqura.properties
  // which contains a line like
  // 1493=30 29 30 29 30 29 29 30 29 30 30 30
  // this test ensures that we don't try to remove this line from the JDK
  public static void <caret>main(String[] args) {
    String s = "1493";
  }
}
