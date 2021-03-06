package java.sql;

import java.util.Calendar;
import java.util.Map;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;

public interface ResultSet {

  boolean next() throws SQLException;

  void close() throws SQLException;

  boolean wasNull() throws SQLException;

  String getString(int columnIndex) throws SQLException;

  boolean getBoolean(int columnIndex) throws SQLException;

  byte getByte(int columnIndex) throws SQLException;

  short getShort(int columnIndex) throws SQLException;

  int getInt(int columnIndex) throws SQLException;

  long getLong(int columnIndex) throws SQLException;

  float getFloat(int columnIndex) throws SQLException;


  double getDouble(int columnIndex) throws SQLException;

  @Deprecated
  BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException;


  byte[] getBytes(int columnIndex) throws SQLException;


  java.sql.Date getDate(int columnIndex) throws SQLException;


  java.sql.Time getTime(int columnIndex) throws SQLException;


  java.sql.Timestamp getTimestamp(int columnIndex) throws SQLException;


  java.io.InputStream getAsciiStream(int columnIndex) throws SQLException;


  @Deprecated
  java.io.InputStream getUnicodeStream(int columnIndex) throws SQLException;


  java.io.InputStream getBinaryStream(int columnIndex)
    throws SQLException;


  // Methods for accessing results by column label


  String getString(String columnLabel) throws SQLException;


  boolean getBoolean(String columnLabel) throws SQLException;


  byte getByte(String columnLabel) throws SQLException;


  short getShort(String columnLabel) throws SQLException;


  int getInt(String columnLabel) throws SQLException;


  long getLong(String columnLabel) throws SQLException;


  float getFloat(String columnLabel) throws SQLException;


  double getDouble(String columnLabel) throws SQLException;


  @Deprecated
  BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException;


  byte[] getBytes(String columnLabel) throws SQLException;


  java.sql.Date getDate(String columnLabel) throws SQLException;


  java.sql.Time getTime(String columnLabel) throws SQLException;


  java.sql.Timestamp getTimestamp(String columnLabel) throws SQLException;


  java.io.InputStream getAsciiStream(String columnLabel) throws SQLException;


  @Deprecated
  java.io.InputStream getUnicodeStream(String columnLabel) throws SQLException;


  java.io.InputStream getBinaryStream(String columnLabel)
    throws SQLException;


  // Advanced features:


  SQLWarning getWarnings() throws SQLException;


  void clearWarnings() throws SQLException;


  String getCursorName() throws SQLException;


  ResultSetMetaData getMetaData() throws SQLException;


  Object getObject(int columnIndex) throws SQLException;


  Object getObject(String columnLabel) throws SQLException;

  //----------------------------------------------------------------


  int findColumn(String columnLabel) throws SQLException;


  //--------------------------JDBC 2.0-----------------------------------

  //---------------------------------------------------------------------
  // Getters and Setters
  //---------------------------------------------------------------------


  java.io.Reader getCharacterStream(int columnIndex) throws SQLException;


  java.io.Reader getCharacterStream(String columnLabel) throws SQLException;


  BigDecimal getBigDecimal(int columnIndex) throws SQLException;


  BigDecimal getBigDecimal(String columnLabel) throws SQLException;

  //---------------------------------------------------------------------
  // Traversal/Positioning
  //---------------------------------------------------------------------


  boolean isBeforeFirst() throws SQLException;


  boolean isAfterLast() throws SQLException;


  boolean isFirst() throws SQLException;


  boolean isLast() throws SQLException;


  void beforeFirst() throws SQLException;


  void afterLast() throws SQLException;


  boolean first() throws SQLException;


  boolean last() throws SQLException;


  int getRow() throws SQLException;

  boolean absolute( int row ) throws SQLException;


  boolean relative( int rows ) throws SQLException;


  boolean previous() throws SQLException;

  //---------------------------------------------------------------------
  // Properties
  //---------------------------------------------------------------------


  int FETCH_FORWARD = 1000;


  int FETCH_REVERSE = 1001;


  int FETCH_UNKNOWN = 1002;


  void setFetchDirection(int direction) throws SQLException;


  int getFetchDirection() throws SQLException;


  void setFetchSize(int rows) throws SQLException;


  int getFetchSize() throws SQLException;


  int TYPE_FORWARD_ONLY = 1003;


  int TYPE_SCROLL_INSENSITIVE = 1004;


  int TYPE_SCROLL_SENSITIVE = 1005;


  int getType() throws SQLException;


  int CONCUR_READ_ONLY = 1007;


  int CONCUR_UPDATABLE = 1008;


  int getConcurrency() throws SQLException;

  //---------------------------------------------------------------------
  // Updates
  //---------------------------------------------------------------------


  boolean rowUpdated() throws SQLException;


  boolean rowInserted() throws SQLException;


  boolean rowDeleted() throws SQLException;


  void updateNull(int columnIndex) throws SQLException;


  void updateBoolean(int columnIndex, boolean x) throws SQLException;


  void updateByte(int columnIndex, byte x) throws SQLException;


  void updateShort(int columnIndex, short x) throws SQLException;


  void updateInt(int columnIndex, int x) throws SQLException;


  void updateLong(int columnIndex, long x) throws SQLException;


  void updateFloat(int columnIndex, float x) throws SQLException;


  void updateDouble(int columnIndex, double x) throws SQLException;


  void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException;


  void updateString(int columnIndex, String x) throws SQLException;


  void updateBytes(int columnIndex, byte x[]) throws SQLException;


  void updateDate(int columnIndex, java.sql.Date x) throws SQLException;


  void updateTime(int columnIndex, java.sql.Time x) throws SQLException;


  void updateTimestamp(int columnIndex, java.sql.Timestamp x)
    throws SQLException;


  void updateAsciiStream(int columnIndex,
                         java.io.InputStream x,
                         int length) throws SQLException;


  void updateBinaryStream(int columnIndex,
                          java.io.InputStream x,
                          int length) throws SQLException;


  void updateCharacterStream(int columnIndex,
                             java.io.Reader x,
                             int length) throws SQLException;


  void updateObject(int columnIndex, Object x, int scaleOrLength)
    throws SQLException;


  void updateObject(int columnIndex, Object x) throws SQLException;


  void updateNull(String columnLabel) throws SQLException;


  void updateBoolean(String columnLabel, boolean x) throws SQLException;


  void updateByte(String columnLabel, byte x) throws SQLException;


  void updateShort(String columnLabel, short x) throws SQLException;


  void updateInt(String columnLabel, int x) throws SQLException;


  void updateLong(String columnLabel, long x) throws SQLException;


  void updateFloat(String columnLabel, float x) throws SQLException;


  void updateDouble(String columnLabel, double x) throws SQLException;


  void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException;


  void updateString(String columnLabel, String x) throws SQLException;


  void updateBytes(String columnLabel, byte x[]) throws SQLException;


  void updateDate(String columnLabel, java.sql.Date x) throws SQLException;


  void updateTime(String columnLabel, java.sql.Time x) throws SQLException;


  void updateTimestamp(String columnLabel, java.sql.Timestamp x)
    throws SQLException;


  void updateAsciiStream(String columnLabel,
                         java.io.InputStream x,
                         int length) throws SQLException;


  void updateBinaryStream(String columnLabel,
                          java.io.InputStream x,
                          int length) throws SQLException;


  void updateCharacterStream(String columnLabel,
                             java.io.Reader reader,
                             int length) throws SQLException;


  void updateObject(String columnLabel, Object x, int scaleOrLength)
    throws SQLException;


  void updateObject(String columnLabel, Object x) throws SQLException;


  void insertRow() throws SQLException;


  void updateRow() throws SQLException;


  void deleteRow() throws SQLException;


  void refreshRow() throws SQLException;


  void cancelRowUpdates() throws SQLException;


  void moveToInsertRow() throws SQLException;


  void moveToCurrentRow() throws SQLException;


  Statement getStatement() throws SQLException;


  Object getObject(int columnIndex, java.util.Map<String,Class<?>> map)
    throws SQLException;


  Ref getRef(int columnIndex) throws SQLException;


  Blob getBlob(int columnIndex) throws SQLException;


  Clob getClob(int columnIndex) throws SQLException;


  Array getArray(int columnIndex) throws SQLException;


  Object getObject(String columnLabel, java.util.Map<String,Class<?>> map)
    throws SQLException;


  Ref getRef(String columnLabel) throws SQLException;


  Blob getBlob(String columnLabel) throws SQLException;


  Clob getClob(String columnLabel) throws SQLException;


  Array getArray(String columnLabel) throws SQLException;


  java.sql.Date getDate(int columnIndex, Calendar cal) throws SQLException;


  java.sql.Date getDate(String columnLabel, Calendar cal) throws SQLException;


  java.sql.Time getTime(int columnIndex, Calendar cal) throws SQLException;


  java.sql.Time getTime(String columnLabel, Calendar cal) throws SQLException;


  java.sql.Timestamp getTimestamp(int columnIndex, Calendar cal)
    throws SQLException;


  java.sql.Timestamp getTimestamp(String columnLabel, Calendar cal)
    throws SQLException;



  int HOLD_CURSORS_OVER_COMMIT = 1;


  int CLOSE_CURSORS_AT_COMMIT = 2;


  java.net.URL getURL(int columnIndex) throws SQLException;


  java.net.URL getURL(String columnLabel) throws SQLException;


  void updateRef(int columnIndex, java.sql.Ref x) throws SQLException;


  void updateRef(String columnLabel, java.sql.Ref x) throws SQLException;


  void updateBlob(int columnIndex, java.sql.Blob x) throws SQLException;


  void updateBlob(String columnLabel, java.sql.Blob x) throws SQLException;


  void updateClob(int columnIndex, java.sql.Clob x) throws SQLException;


  void updateClob(String columnLabel, java.sql.Clob x) throws SQLException;


  void updateArray(int columnIndex, java.sql.Array x) throws SQLException;


  void updateArray(String columnLabel, java.sql.Array x) throws SQLException;

  //------------------------- JDBC 4.0 -----------------------------------


  RowId getRowId(int columnIndex) throws SQLException;


  RowId getRowId(String columnLabel) throws SQLException;


  void updateRowId(int columnIndex, RowId x) throws SQLException;


  void updateRowId(String columnLabel, RowId x) throws SQLException;


  int getHoldability() throws SQLException;


  boolean isClosed() throws SQLException;


  void updateNString(int columnIndex, String nString) throws SQLException;


  void updateNString(String columnLabel, String nString) throws SQLException;


  void updateNClob(int columnIndex, NClob nClob) throws SQLException;


  void updateNClob(String columnLabel, NClob nClob) throws SQLException;


  NClob getNClob(int columnIndex) throws SQLException;


  NClob getNClob(String columnLabel) throws SQLException;


  SQLXML getSQLXML(int columnIndex) throws SQLException;


  SQLXML getSQLXML(String columnLabel) throws SQLException;

  void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException;

  void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException;


  String getNString(int columnIndex) throws SQLException;



  String getNString(String columnLabel) throws SQLException;



  java.io.Reader getNCharacterStream(int columnIndex) throws SQLException;


  java.io.Reader getNCharacterStream(String columnLabel) throws SQLException;


  void updateNCharacterStream(int columnIndex,
                              java.io.Reader x,
                              long length) throws SQLException;


  void updateNCharacterStream(String columnLabel,
                              java.io.Reader reader,
                              long length) throws SQLException;

  void updateAsciiStream(int columnIndex,
                         java.io.InputStream x,
                         long length) throws SQLException;


  void updateBinaryStream(int columnIndex,
                          java.io.InputStream x,
                          long length) throws SQLException;


  void updateCharacterStream(int columnIndex,
                             java.io.Reader x,
                             long length) throws SQLException;

  void updateAsciiStream(String columnLabel,
                         java.io.InputStream x,
                         long length) throws SQLException;


  void updateBinaryStream(String columnLabel,
                          java.io.InputStream x,
                          long length) throws SQLException;


  void updateCharacterStream(String columnLabel,
                             java.io.Reader reader,
                             long length) throws SQLException;

  void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException;


  void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException;


  void updateClob(int columnIndex,  Reader reader, long length) throws SQLException;


  void updateClob(String columnLabel,  Reader reader, long length) throws SQLException;

  void updateNClob(int columnIndex,  Reader reader, long length) throws SQLException;


  void updateNClob(String columnLabel,  Reader reader, long length) throws SQLException;

  //---


  void updateNCharacterStream(int columnIndex,
                              java.io.Reader x) throws SQLException;


  void updateNCharacterStream(String columnLabel,
                              java.io.Reader reader) throws SQLException;

  void updateAsciiStream(int columnIndex,
                         java.io.InputStream x) throws SQLException;


  void updateBinaryStream(int columnIndex,
                          java.io.InputStream x) throws SQLException;


  void updateCharacterStream(int columnIndex,
                             java.io.Reader x) throws SQLException;

  void updateAsciiStream(String columnLabel,
                         java.io.InputStream x) throws SQLException;


  void updateBinaryStream(String columnLabel,
                          java.io.InputStream x) throws SQLException;


  void updateCharacterStream(String columnLabel,
                             java.io.Reader reader) throws SQLException;

  void updateBlob(int columnIndex, InputStream inputStream) throws SQLException;


  void updateBlob(String columnLabel, InputStream inputStream) throws SQLException;


  void updateClob(int columnIndex,  Reader reader) throws SQLException;


  void updateClob(String columnLabel,  Reader reader) throws SQLException;

  void updateNClob(int columnIndex,  Reader reader) throws SQLException;


  void updateNClob(String columnLabel,  Reader reader) throws SQLException;

  //------------------------- JDBC 4.1 -----------------------------------



  public <T> T getObject(int columnIndex, Class<T> type) throws SQLException;



  public <T> T getObject(String columnLabel, Class<T> type) throws SQLException;

  //------------------------- JDBC 4.2 -----------------------------------


  default void updateObject(int columnIndex, Object x,
                            SQLType targetSqlType, int scaleOrLength)  throws SQLException {
    throw new SQLFeatureNotSupportedException("updateObject not implemented");
  }

  default void updateObject(String columnLabel, Object x,
                            SQLType targetSqlType, int scaleOrLength) throws SQLException {
    throw new SQLFeatureNotSupportedException("updateObject not implemented");
  }


  default void updateObject(int columnIndex, Object x, SQLType targetSqlType)
    throws SQLException {
    throw new SQLFeatureNotSupportedException("updateObject not implemented");
  }


  default void updateObject(String columnLabel, Object x,
                            SQLType targetSqlType) throws SQLException {
    throw new SQLFeatureNotSupportedException("updateObject not implemented");
  }
}