class C {
  public boolean foo(String type, String text) {
    if ("I".equals(type) || "L".equals(type)) {
      return <weak_warning descr="Multiple occurrences of '!text.startsWith(\"0x\") && !text.startsWith(\"0X\")'">!text.startsWith("0x") && !text.startsWith("0X")</weak_warning>;
    }
    if ("D".equals(type) || "F".equals(type)) {
      return <weak_warning descr="Multiple occurrences of '!text.startsWith(\"0x\") && !text.startsWith(\"0X\")'">!text.startsWith("0x") && !text.startsWith("0X")</weak_warning>;
    }
    return false;
  }
}