record LongRecord(String s1, String s2, String s3) {}
record PrimitiveRecord(int x){}
record IntegerRecord(Integer x){}
record RecordWithInterface(I x, I y) {}

sealed interface I permits C, D {}
final class C implements I {}
final class D implements I {}

record TypedRecord<T>(T x) {}

public class Incompatible {

  Object object;
  Integer integer;
  TypedRecord<I> typedRecord;

  <T extends IntegerRecord> void incompatible() {
    switch (object) {
      case LongRecord(String s1, <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">int x</error>, <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">int y</error>) -> {}
      case RecordWithInterface(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'I'">Integer x</error>, <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'I'">Integer y</error>) s when true -> {}
      case RecordWithInterface(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'I'">Integer x</error>, D y) s when true -> {}
      case RecordWithInterface(I x, D y) s when true -> {}
      case <error descr="Deconstruction pattern can only be applied to a record">Integer</error>(double x) -> {}
      case PrimitiveRecord(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'int'">Integer x</error>) s when true -> {}
      case PrimitiveRecord(int x) s when true -> {}
      case IntegerRecord(Integer x) s when true -> {}
      case IntegerRecord(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Integer'">int x</error>) s when true -> {}
      case <error descr="'java.lang.Object' cannot be safely cast to 'T'">T(Integer x)</error> -> {}

    }
    switch (integer){
      case <error descr="Incompatible types. Found: 'PrimitiveRecord', required: 'java.lang.Integer'">PrimitiveRecord(int x)</error> -> {}
      default -> {}
    }
    switch (typedRecord){
      case TypedRecord<I>(I x) s-> {}
      default -> {}
    }
  }
}