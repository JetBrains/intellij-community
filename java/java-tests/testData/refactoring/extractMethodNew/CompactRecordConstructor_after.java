record RecordBug(String title, int amount) {
  RecordBug {
      newMethod(amount);

  }

    private void newMethod(int amount) {
        if (amount < 0) {
          throw new IllegalArgumentException("amount must be positive");
        }
    }
}