public class Res {

    void bar(R r){}

    interface R<S, T> {
        void la(S s, T t);
    }

    private void validateStructuresCookie(Res cookie) {
        cookie.bar(new <caret>R<String, Object>(){
            final String param = "";
            @Override
            public void la(String s, Object o) {
                System.out.println(param);
            }
        });
      }
}