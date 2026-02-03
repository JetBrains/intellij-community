public class Devk {

    void fr(String... params ) {
        int idx = 0;
        newMethod(idx, params);
    }

    private void newMethod(int idx, String[] params) {
        System.out.println(params[idx++]);
        System.out.println(params[idx++]);
        System.out.println(params[idx++]);
        System.out.println(params[idx++]);
    }
}