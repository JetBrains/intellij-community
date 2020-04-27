// "Convert canonical constructor to compact form" "true" 
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

record Rec(@Anno int x, int y) {
    public Rec {
        this.x = y;
    }
}

@Target(ElementType.FIELD)
@interface Anno {}