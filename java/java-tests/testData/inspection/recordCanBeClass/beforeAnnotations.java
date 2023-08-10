// "Convert record to class" "true-preview"
import java.lang.annotation.*;

@Target(ElementType.FIELD)
@interface FieldAnno {int value();}

@Target(ElementType.FIELD)
@interface FieldAnno2 {int value();}

@Target(ElementType.METHOD)
@interface MethodAnno {int value();}

@Target(ElementType.PARAMETER)
@interface ParameterAnno {int value();}

@Target(ElementType.TYPE_USE)
@interface TypeUse {int value();}

@Target(ElementType.TYPE_USE)
@interface TypeUse2 {int value();}

@Target(ElementType.TYPE)
@interface TypeAnno {}

@TypeAnno
record <caret>R(@FieldAnno(/*1*/1) @MethodAnno(1) @TypeUse(1) @FieldAnno2(1) @ParameterAnno(1) int f1,
                @FieldAnno(2) @TypeUse(2) @TypeUse2(2) int f2,
                @MethodAnno(3) int f3,
                int f4) implements F3 {
  @MethodAnno(4)
  @Override  
  public int f4() {
    return f4;
  }
}

interface F3 {
  int f3();
}