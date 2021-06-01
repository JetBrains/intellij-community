public class <symbolName descr="null">Simple</symbolName> {
private final int <symbolName descr="null">bar</symbolName> = 0;
private class <symbolName descr="null">Inner</symbolName> { }
private int <symbolName descr="null">foo</symbolName>() { return 0;}

  void <symbolName descr="null">bazz</symbolName>() {
  int <symbolName descr="null">x</symbolName> =
new <symbolName descr="null" type="CONSTRUCTOR_CALL" foreground="0x000000" effectcolor="0xffc800" effecttype="BOXED" fonttype="0">Inner</symbolName>().<symbolName descr="null">hashCode</symbolName>() + 
<symbolName descr="null" type="INSTANCE_FINAL_FIELD" foreground="0x660e7a" effectcolor="0xffc800" effecttype="BOXED" fonttype="1">bar</symbolName> + 
<symbolName descr="null" type="METHOD_CALL" foreground="0x000000" effectcolor="0xffc800" effecttype="BOXED" fonttype="0">foo</symbolName>();
  }
  }