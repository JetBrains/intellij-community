public class DupSlice {
    int <caret>field; 

    void multiply(int fp) {
        this.field = fp;
        multiplay2(1111111111);
    }

    void multiplay2(int i) {
        field = i;
        multiply(i);
    }
}
