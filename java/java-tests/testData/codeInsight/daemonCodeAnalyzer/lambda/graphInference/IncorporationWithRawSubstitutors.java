import java.io.Serializable;
import java.util.function.BiFunction;

class Test {

  interface HasCode<T extends Serializable> {

    static <U extends Serializable, T extends Enum<T> & HasCode<U>> T fromCode(U code, Class<T> classEnum) {
      return null;
    }
  }

  enum EnumRaw implements HasCode{
    RAW_VALUE1;
  }

  public static <T, U extends Serializable, R> R checkExpected(BiFunction<T, U, R> funct, T param, U secondParam, R expectedResult){
    R result = funct.apply(param, secondParam);
    return result;
  }

  public static void main(String[] args){
    checkExpected(HasCode::fromCode, "RAW_VALUE2", EnumRaw.class, EnumRaw.RAW_VALUE1);
  }

}
