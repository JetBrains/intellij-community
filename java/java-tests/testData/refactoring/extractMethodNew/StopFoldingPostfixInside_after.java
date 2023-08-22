public class Devk {

    void fr(String... params ) {
        int idx = 0;
        newMethod(params, idx);
    }

    private void newMethod(String[] params, int idx) {
        System.out.println(params[idx++]);
        System.out.println(params[idx++]);
        System.out.println(params[idx++]);
        System.out.println(params[idx++]);
    }
}