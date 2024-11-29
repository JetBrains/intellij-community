import java.util.Map;
import java.util.stream.*;

class Either<E>{
  public  boolean isRight() {
    return false;
  }
}
class TestClass {
  Map<Boolean, Either<String>> test(final Stream<Either<String>> eitherStream) {
    return <error descr="Incompatible types. Found: 'java.util.Map<java.lang.Boolean,java.util.List<Either<java.lang.String>>>', required: 'java.util.Map<java.lang.Boolean,Either<java.lang.String>>'">eitherStream.collect(Collectors.groupingBy(Either::isRight));</error>
  }
}