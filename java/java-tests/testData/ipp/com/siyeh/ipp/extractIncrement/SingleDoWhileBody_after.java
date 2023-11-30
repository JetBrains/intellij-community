public class Main {
    int i = 3;

    public void foo() {
        do {
            Syste<caret>m.out.println(i);
            i++;
        }
        while (true);
    }
}