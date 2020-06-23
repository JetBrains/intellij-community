public class Test {
    public void test(){
        for (int i=0; i<10; i++){
            if (newMethod(i)) break;
        }
    }

    private boolean newMethod(int i) {
        return i == 5;
    }
}