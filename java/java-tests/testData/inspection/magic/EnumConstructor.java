import org.intellij.lang.annotations.MagicConstant;

enum EnumConstructor {
  FOO(<warning descr="Should be one of: MagicConstantIds.ONE">1</warning>),
  ;

  private final int magicConstant;

  EnumConstructor(@MagicConstant(valuesFromClass = MagicConstantIds.class) int magicConstant) {
    this.magicConstant = magicConstant;
  }
}
class MagicConstantIds {
  static final int ONE = 1;
  private static final int TWO = 2;
}
