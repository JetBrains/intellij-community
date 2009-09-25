import java.sql.*;

public class Test {

  /**
   * Lädt den Datensatz.
   */
  private boolean loadValues(Transaction transaction) {
    try {
      boolean res=false;
      PreparedStatement stmt=transaction.dirty() ? transaction.dbstore().stmtGetDirty(this) : transaction.dbstore().stmtGetClean(this);
      if (stmt==null) return false;
      synchronized(stmt) {
        try {
          transaction.dbmanager.dbstore().loadsCount++;
          try {
            fillPHPK(stmt, transaction);
          } catch (SQLException e) {
            DBManager.resetThread(stmt);
            throw e;
          }
          ResultSet rs=stmt.executeQuery();
          try {
            if (rs.next()) {
              loadOrdered(rs);
              res=true;
            } else if (transaction.dbmanager.accessCommitBug()) {
              // Scheint manchmal in Access zu passieren....
              PreparedStatement stmt1=transaction.dirty() ? stmtGetDirty(transaction.dbstore()) : stmtGetClean(transaction.dbstore());
              synchronized(stmt1)
              {
                fillPHPK(stmt1, transaction);
                ResultSet rs1=stmt1.executeQuery();
                if (rs1.next()) {
                  Dbg.pw("Second try to load object returns an object: "+toString()+"???");
                  loadOrdered(rs1);
                  res=true;
                }
                rs1.close();
                stmt1.close();
              }
            }
          } finally {
            try {
              rs.close();
            } catch (SQLException e) {
              Dbg.pw(e);
            }
          }
        } catch (SQLException e) {
          handleSQLException(e, stmt);
        }
      }
      if (res) loadBlobs(transaction);
      return res;
    } catch (SQLException e) {
      throw new ExceptionBag(e);
    }
  }

}
