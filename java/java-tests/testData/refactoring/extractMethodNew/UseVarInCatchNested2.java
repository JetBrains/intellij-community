import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;

class A {
    void f() {
        int i = 0;
        try {
            i = 1;
            try {
                i = 2;
                if (r()) throw new IOException();
                <selection>i = 3;
                if (r()) throw new SQLException();
                i = 4;</selection>
                System.out.println("ok");
            } catch (IOException e) {
                System.out.println("io " + i);
            }
        } catch (SQLException e) {
            System.out.println("sql " + i);
        }
    }

    private boolean r() {
        return ThreadLocalRandom.current().nextBoolean();
    }
}