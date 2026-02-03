class NoConstructor {
  Writer writer;

  public void close() {
    try {
      writer.close();
    } catch (IOException e) {
      final RuntimeException exception = new RuntimeException(e);
      exception.<caret>initCause(e);
      throw exception;
    }
  }
}