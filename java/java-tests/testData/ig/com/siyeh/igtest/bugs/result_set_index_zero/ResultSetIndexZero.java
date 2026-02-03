import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ResultSetIndexZero {
    private static final int COLUMN_INDEX = 0;

    public void foo(ResultSet resultSet) throws SQLException {
        resultSet.getInt(<warning descr="Use of index '0' in JDBC ResultSet">0</warning>);
        resultSet.getInt(<warning descr="Use of index '0' in JDBC ResultSet">COLUMN_INDEX</warning>);
        resultSet.getInt(3);
    }

  void foo(PreparedStatement ps) throws SQLException {
    ps.setQueryTimeout(0);
    ps.setFetchDirection(0);
    ps.setFetchSize(0);
    ps.setMaxFieldSize(0);
    ps.setMaxRows(0);
  }
}
