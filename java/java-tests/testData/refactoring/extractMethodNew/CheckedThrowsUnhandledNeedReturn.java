import java.io.IOException;
import java.sql.SQLException;

class Test {
    String test(boolean b, boolean ok) throws IOException, SQLException {
        if (ok) {
            return "";
        }
        <selection>if (b) {
            throw new IOException();
        } else {
            throw new SQLException();
        }</selection>
    }
}
