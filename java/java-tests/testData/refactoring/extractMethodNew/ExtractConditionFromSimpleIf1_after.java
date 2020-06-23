public class Test {
    public void test(){
        for (int i=0; i<10; i++){
            newMethod(i);
        }
    }

    private void newMethod(int i) {
        if (i == 5) System.out.println();
    }
}