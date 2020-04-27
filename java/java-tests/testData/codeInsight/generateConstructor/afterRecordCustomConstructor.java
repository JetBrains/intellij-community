import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

record Test(int x,int y,int z) {
    public Test(int x, int y, int z) {<caret>
        this.x = x;
        this.y = y;
        this.z = z;
    }
} 
