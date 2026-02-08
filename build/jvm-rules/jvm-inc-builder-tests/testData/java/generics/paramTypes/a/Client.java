import java.util.Set;

public class Client {
    public static void main(String[] args) {
        final Server server = new Server();
        final Set<DataDerived> set = null;
        server.foo(set);
    }
}
