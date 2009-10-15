public class Main {
    interface Account {}

    public static void main(String[] args) {
        java.util.List list;
        for (int i = 0; i < list.size(); i++) {
          Object o = (Account<caret>list.get(i);
          System.out.println(o);
        }
    }
}