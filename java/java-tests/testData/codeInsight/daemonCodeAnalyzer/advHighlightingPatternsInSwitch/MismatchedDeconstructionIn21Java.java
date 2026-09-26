package com.test;

import java.util.List;

record LongRecord(String s1, String s2, String s3) {}
record PrimitiveRecord(int x){}
record IntegerRecord(Integer x){}
record RecordWithInterface(I x, I y) {}
record Top(Child c1, Child c2) {}
record Child(I x, I y){}
record Wrong(int x) {}

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
      case LongRecord<error descr="Incorrect number of nested patterns: expected 3 but found 2">(String s1, int y)</error> -> {}
      case RecordWithInterface<error descr="Incorrect number of nested patterns: expected 2 but found 1">(Integer y)</error> when true -> {}
      case RecordWithInterface(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'com.test.I'">Integer x</error>, D y) when true -> {}
      case RecordWithInterface(I x, D y) when true -> {}
      case <error descr="Deconstruction pattern can only be applied to a record, 'java.lang.Integer' is not a record">Integer</error>(double x) -> {}
      case PrimitiveRecord(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '21'">Integer x</error>) when true -> {}
      case PrimitiveRecord(int x) when true -> {}
      case IntegerRecord(Integer x) when true -> {}
      case IntegerRecord(<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '21'">int x</error>) when true -> {}
      case <error descr="'Object' cannot be safely cast to 'T'"><error descr="Deconstruction pattern can only be applied to a record, 'T' is not a record">T</error>(Integer x)</error> -> {}

    }
    switch (integer){
      case <error descr="Incompatible types. Found: 'com.test.PrimitiveRecord', required: 'java.lang.Integer'">PrimitiveRecord(int x)</error> -> {}
      default -> {}
    }
    switch (typedRecord){
      case TypedRecord<I>(I x)-> {}
      default -> {}
    }
    switch (typedRecord){
      case TypedRecord(C x)-> {}
      case TypedRecord(I x)-> {}
      case TypedRecord<I>(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'com.test.I'">Integer t</error>) -> {}
      case TypedRecord<?>(<error descr="'Object' cannot be safely cast to 'List<Number>'">List<Number> nums</error>) -> {}
      case TypedRecord<?>(List<?> list) -> {}
      case TypedRecord<?>(<error descr="'Object' cannot be safely cast to 'T'">T t</error>) -> {}
      case TypedRecord<?>(String s) -> {}
      case TypedRecord<?>(var x) -> {}
      default -> {}
    }
    switch (object){
      case Top(Child c1, Child(I x, <error descr="Incompatible types. Found: 'int', required: 'com.test.I'">int y</error>)) -> {  }
      case Top(Child c1, <error descr="Incompatible types. Found: 'com.test.Wrong', required: 'com.test.Child'">Wrong(int y)</error>) -> {  }
      case Top(Child c1, Child(C a, I i)) -> {  }
      default -> {}
    }
  }
}