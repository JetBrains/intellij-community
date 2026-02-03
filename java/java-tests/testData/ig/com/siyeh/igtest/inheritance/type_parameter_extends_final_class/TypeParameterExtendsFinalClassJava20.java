package com.siyeh.igtest.inheritance.type_parameter_extends_final_class;

import java.util.*;



public class TypeParameterExtendsFinalClassJava20{
  public static void testGenericWithWildcard(List<RecordWithGeneric<<warning descr="Wildcard type argument '?' extends 'final' class 'String'">?</warning> extends String>> list){
    for (RecordWithGeneric<? extends String>(var x): list) {
      System.out.println(x);
    }
  }

  public static void testGenericWithoutWildcard(List<RecordWithGeneric<String>> list){
    for (RecordWithGeneric<<warning descr="Wildcard type argument '?' extends 'final' class 'String'">?</warning> extends String>(var x): list) {
      System.out.println(x);
    }
  }

  public static void testGenericWithoutWildcard2(List<RecordWithGeneric<RecordWithGeneric<String>>> list){
    for (RecordWithGeneric<RecordWithGeneric<String>>(RecordWithGeneric<<warning descr="Wildcard type argument '?' extends 'final' class 'String'">?</warning> extends String>(var x)): list) {
      System.out.println(x);
    }
  }

  public static void testRecordWithWildcard(){
    List<RecordWithWildcard> lists2 = List.of(new RecordWithWildcard(List.of("1"), "2"));
    for (RecordWithWildcard(List<? extends String> x, var y ) : lists2) {
      System.out.println(x);
    }
  }

  public static void testRecordWithoutWildcard(){
    List<RecordWithoutWildcard> lists3 = List.of(new RecordWithoutWildcard(List.of("1"), "2"));
    for (RecordWithoutWildcard(List<<warning descr="Wildcard type argument '?' extends 'final' class 'String'">?</warning> extends String> x, var y ) : lists3) {
      System.out.println(x);
    }
  }
}


record RecordWithWildcard(List<<warning descr="Wildcard type argument '?' extends 'final' class 'String'">?</warning> extends String> x, String y){

}
record RecordWithoutWildcard(List<String> x, String y){

}

record RecordWithGeneric<T>(T x){

}