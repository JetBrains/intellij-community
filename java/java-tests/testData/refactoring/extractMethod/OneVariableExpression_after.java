import java.util.List;

class C {
    void m(List<String> list) {
        int i = 0;
        System.out.println(newMethod(i));

        if (list.size() > 0) {
            System.out.println(list.size());
        }
    }

    private int newMethod(int i) {
        return i;
    }
}