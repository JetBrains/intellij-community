import org.intellij.lang.annotations.MagicConstant;

class Main {
  static final int V1 = 10;
  static final int V2 = 20;

  void consume(@MagicConstant(intValues = {V1, V2}) int x) {

  }
}
class Use {
  int field;
  int fieldOk;
  @MagicConstant(intValues = {Main.V1, Main.V2})
  int fieldOkAnnotated;

  Use() {
    this.field = 123;
    this.fieldOk = Main.V1;
    this.fieldOkAnnotated = Main.V2;
    test(123, Main.V1, Main.V2);
  }

  void test(int parameter, int parameterOk, @MagicConstant(intValues = {Main.V1, Main.V2}) int parameterOkAnnotated) {
    int local = 123;
    int localOk = Main.V1;
    new Main().consume(<warning descr="Should be one of: Main.V1, Main.V2">field</warning>);
    new Main().consume(<warning descr="Should be one of: Main.V1, Main.V2">fieldOk</warning>);
    new Main().consume(fieldOkAnnotated);
    new Main().consume(<warning descr="Should be one of: Main.V1, Main.V2">local</warning>);
    new Main().consume(localOk);
    new Main().consume(<warning descr="Should be one of: Main.V1, Main.V2">parameter</warning>);
    new Main().consume(<warning descr="Should be one of: Main.V1, Main.V2">parameterOk</warning>);
    new Main().consume(parameterOkAnnotated);
  }
}
