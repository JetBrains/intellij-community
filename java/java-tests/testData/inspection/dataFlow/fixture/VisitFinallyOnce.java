import java.io.IOException;

class TestIDEAWarn {
  private Connection _connection;

  public void warn() throws IOException {
    try {
      if (_connection != null) {
        try {
          _connection.commit();
        } finally {
          _connection.close();
          _connection = <warning descr="Assigning 'null' value to non-annotated field">null</warning>;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void warn2() throws IOException {
    if (_connection == null) return;
    try {
      try {
        _connection.commit();
      } finally {
        _connection.close();
        _connection = <warning descr="Assigning 'null' value to non-annotated field">null</warning>;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  interface Connection {
    void commit() throws IOException;
    void close() throws IOException;
  }
}
