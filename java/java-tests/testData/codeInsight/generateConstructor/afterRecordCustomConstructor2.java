import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

record Test(int x,int y,int z) {
    Test(int x, int y) {<caret>
        this(x, y, 0);
    }
} 
