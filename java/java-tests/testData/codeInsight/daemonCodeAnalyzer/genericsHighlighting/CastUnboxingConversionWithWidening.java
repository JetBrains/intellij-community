public class CastUnboxingConversionWithWidening {
  static <T extends Integer> double boundIntegerToDoublePrimitive1(T i) {
    return (double) i; //no report
  }

  static <T extends Integer> double boundIntegerToDoublePrimitive2(T i) {
    double i1 = i; //no report
    return i1;
  }

  static <T extends Integer> double boundIntegerToDoublePrimitive3(T i) {
    double i1 = (double) i; //no report
    return i1;
  }

  static <T extends Double> int boundDoubleToIntegerPrimitive(T i) {
    int i1 = <error descr="Incompatible types. Found: 'T', required: 'int'">i</error>; //report
    return i1;
  }
}