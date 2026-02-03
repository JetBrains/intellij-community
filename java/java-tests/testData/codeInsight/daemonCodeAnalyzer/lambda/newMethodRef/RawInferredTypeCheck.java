import java.io.Serializable;
import java.util.function.BiConsumer;

class Test {

  interface HasCode<T extends Serializable> {
    static <U extends Serializable, T extends Enum<T> & HasCode<U>> T fromCode(U code, Class<T> classEnum) {
      return null;
    }
  }

  enum EnumRaw implements HasCode {
    ;
  }

  public static void main(String[] args){
    final BiConsumer<String, Class<EnumRaw>> code = HasCode::fromCode;
  }

}
