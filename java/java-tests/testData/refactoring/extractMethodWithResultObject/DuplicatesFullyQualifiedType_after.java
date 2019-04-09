public class FullyQualifiedType {
    void foo(String s) throws Exception {
        try {
            NewMethodResult x = newMethod(s);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    NewMethodResult newMethod(String s) throws Exception {
        java.net.URL url = parse(s, java.net.URL.class);
        System.out.println(url);
        if (url != null) {
            System.out.println(url.toExternalForm());
            System.out.println(url.getProtocol());
        }
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    void bar(String s) throws Exception {
        try {
            java.net.URL url = parse(s, java.net.URL.class);
            System.out.println(url);
            if (url != null) {
                System.out.println(url.toExternalForm());
                System.out.println(url.getProtocol());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static <T> T parse(String s, Class<T> aClass) throws Exception {
        return aClass.getDeclaredConstructor(String.class).newInstance(s);
    }
}
