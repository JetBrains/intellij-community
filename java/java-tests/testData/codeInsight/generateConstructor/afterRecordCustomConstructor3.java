import java.util.Optional;

record Test(int x,int y, boolean a, double b, Optional<String> opt, int[] data) {
    Test(int x, int y) {<caret>
        this(x, y, false, 0, Optional.empty(), new int[0]);
    }
} 
