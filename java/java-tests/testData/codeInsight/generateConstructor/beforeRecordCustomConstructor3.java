import java.util.Optional;

record Test(int x,int y, boolean a, double b, Optional<String> opt, int[] data) {
    <caret>
} 
