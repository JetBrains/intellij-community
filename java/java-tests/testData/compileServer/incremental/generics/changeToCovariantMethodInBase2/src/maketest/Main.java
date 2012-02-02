package maketest;

public class Main {
    public static void main(String[] args) {
        I i = new IImpl();
        Data data = i.getData();
        System.out.println("data = " + data);
    }
}
