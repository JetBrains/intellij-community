public class Res {

    public static final Res.R<String, Object> R = new R<String, Object>() {
        final String param = "";

        @Override
        public void la(String s, Object o) {
            System.out.println(param);
        }
    };

    void bar(R r){}

    interface R<S, T> {
        void la(S s, T t);
    }

    private void validateStructuresCookie(Res cookie) {
        cookie.bar(<caret>R);
      }
}