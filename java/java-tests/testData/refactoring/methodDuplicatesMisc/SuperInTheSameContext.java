public class a {
    public void login() {
        System.out.println();
    }
}

class b extends a {
    public void doL<caret>ogin() throws Exception {
        super.login();
    }
}

class c extends a {
    public void doLogin() throws Exception {
        super.login();
    }
}