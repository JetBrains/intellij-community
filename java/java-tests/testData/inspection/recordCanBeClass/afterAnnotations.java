// "Convert record to class" "true-preview"
import java.lang.annotation.*;
import java.util.Objects;

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
final class R implements F3 {
    @FieldAnno(/*1*/1)
    @FieldAnno2(1)
    private final @TypeUse(1) int f1;
    @FieldAnno(2)
    private final @TypeUse(2)
    @TypeUse2(2) int f2;
    private final int f3;
    private final int f4;

    R(@TypeUse(1) @ParameterAnno(1) int f1,
      @TypeUse(2) @TypeUse2(2) int f2,
      int f3,
      int f4) {
        this.f1 = f1;
        this.f2 = f2;
        this.f3 = f3;
        this.f4 = f4;
    }

    @MethodAnno(4)
    public int f4() {
        return f4;
    }

    @MethodAnno(1)
    public @TypeUse(1) int f1() {
        return f1;
    }

    public @TypeUse(2) @TypeUse2(2) int f2() {
        return f2;
    }

    @Override
    @MethodAnno(3)
    public int f3() {
        return f3;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (R) obj;
        return this.f1 == that.f1 &&
                this.f2 == that.f2 &&
                this.f3 == that.f3 &&
                this.f4 == that.f4;
    }

    @Override
    public int hashCode() {
        return Objects.hash(f1, f2, f3, f4);
    }

    @Override
    public String toString() {
        return "R[" +
                "f1=" + f1 + ", " +
                "f2=" + f2 + ", " +
                "f3=" + f3 + ", " +
                "f4=" + f4 + ']';
    }

}

interface F3 {
  int f3();
}