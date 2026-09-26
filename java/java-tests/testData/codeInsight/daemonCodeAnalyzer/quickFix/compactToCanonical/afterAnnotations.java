// "Convert compact constructor to canonical" "true-preview"
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public record Test(int x, @ParamAnno int y, @FieldAnno int... other) {
    @ConstructorAnno
    public Test(int x, @ParamAnno int y, int... other) {
        this.x = Math.abs(x);
        if (other == null) other = new int[0];
        this.y = y;
        this.other = other;
    }
}

@Target(ElementType.PARAMETER)
@interface ParamAnno {}

@Target(ElementType.FIELD)
@interface FieldAnno {}

@Target(ElementType.CONSTRUCTOR)
@interface ConstructorAnno {}
