record RecordBug(String title, int amount) {
  RecordBug {
    <selection>if (amount < 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    </selection>
  }
}