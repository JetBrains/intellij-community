import java.lang.annotation.ElementType;

class EnumSynthetics {
  void m() {
    //ElementType[] values = ElementType.values();
    ElementType type = ElementType.valueOf("TYPE");
  }
}
